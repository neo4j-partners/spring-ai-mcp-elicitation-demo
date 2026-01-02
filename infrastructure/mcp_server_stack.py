"""CDK Stack for Spring AI MCP Elicitation Demo on AgentCore Runtime.

This stack deploys the Spring AI MCP server to Amazon Bedrock AgentCore Runtime using:
- Local Docker build (fast iteration)
- ECR for image storage
- Cognito for JWT authentication
- AgentCore Runtime with MCP protocol
"""
from aws_cdk import (
    Stack,
    aws_ecr_assets as ecr_assets,
    aws_cognito as cognito,
    aws_bedrockagentcore as bedrockagentcore,
    aws_lambda as lambda_,
    aws_iam as iam,
    CfnParameter,
    CfnOutput,
    Duration,
    CustomResource,
    RemovalPolicy,
)
from constructs import Construct
from infra_utils.agentcore_role import AgentCoreRole


class McpServerStack(Stack):
    """CDK Stack for deploying Spring AI MCP server to AgentCore Runtime."""

    def __init__(self, scope: Construct, construct_id: str, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        # Parameters
        agent_name = CfnParameter(
            self,
            "AgentName",
            type="String",
            default="SpringAiMcpServer",
            description="Name for the MCP server runtime",
        )

        network_mode = CfnParameter(
            self,
            "NetworkMode",
            type="String",
            default="PUBLIC",
            description="Network mode for AgentCore resources",
            allowed_values=["PUBLIC", "PRIVATE"],
        )

        # Build Docker image locally and push to ECR
        # The Dockerfile.server is at the project root
        mcp_server_image = ecr_assets.DockerImageAsset(
            self,
            "MCPServerImage",
            directory="..",  # Project root
            file="Dockerfile.server",
            platform=ecr_assets.Platform.LINUX_ARM64,
            exclude=[
                "infrastructure/cdk.out",
                "infrastructure/.venv",
                "client/build",
                "server/build",
                ".gradle",
                ".git",
            ],
        )

        # AgentCore execution role
        agent_role = AgentCoreRole(self, "AgentCoreRole")

        # Cognito User Pool for JWT authentication
        user_pool = cognito.UserPool(
            self,
            "MCPUserPool",
            user_pool_name=f"{self.stack_name}-user-pool",
            password_policy=cognito.PasswordPolicy(
                min_length=8,
                require_uppercase=False,
                require_lowercase=False,
                require_digits=False,
                require_symbols=False,
            ),
            self_sign_up_enabled=False,
            sign_in_aliases=cognito.SignInAliases(username=True),
            removal_policy=RemovalPolicy.DESTROY,
        )

        # Cognito User Pool Client
        user_pool_client = cognito.UserPoolClient(
            self,
            "MCPUserPoolClient",
            user_pool=user_pool,
            user_pool_client_name=f"{self.stack_name}-client",
            generate_secret=False,
            auth_flows=cognito.AuthFlow(
                user_password=True,
                user_srp=True,
            ),
        )

        # Create test user
        test_user = cognito.CfnUserPoolUser(
            self,
            "TestUser",
            user_pool_id=user_pool.user_pool_id,
            username="testuser",
            message_action="SUPPRESS",
        )

        # Lambda function to set user password
        password_setter_role = iam.Role(
            self,
            "PasswordSetterRole",
            assumed_by=iam.ServicePrincipal("lambda.amazonaws.com"),
            managed_policies=[
                iam.ManagedPolicy.from_aws_managed_policy_name(
                    "service-role/AWSLambdaBasicExecutionRole"
                )
            ],
            inline_policies={
                "CognitoAdmin": iam.PolicyDocument(
                    statements=[
                        iam.PolicyStatement(
                            effect=iam.Effect.ALLOW,
                            actions=["cognito-idp:AdminSetUserPassword"],
                            resources=[user_pool.user_pool_arn],
                        )
                    ]
                )
            },
        )

        password_setter_fn = lambda_.Function(
            self,
            "PasswordSetterFunction",
            runtime=lambda_.Runtime.PYTHON_3_12,
            handler="index.handler",
            role=password_setter_role,
            timeout=Duration.minutes(1),
            code=lambda_.Code.from_inline(
                """
import boto3
import cfnresponse

def handler(event, context):
    if event['RequestType'] == 'Delete':
        cfnresponse.send(event, context, cfnresponse.SUCCESS, {})
        return

    try:
        cognito = boto3.client('cognito-idp')
        cognito.admin_set_user_password(
            UserPoolId=event['ResourceProperties']['UserPoolId'],
            Username=event['ResourceProperties']['Username'],
            Password=event['ResourceProperties']['Password'],
            Permanent=True
        )
        cfnresponse.send(event, context, cfnresponse.SUCCESS, {'Status': 'SUCCESS'})
    except Exception as e:
        cfnresponse.send(event, context, cfnresponse.FAILED, {'Error': str(e)})
"""
            ),
        )

        # Set test user password via custom resource
        set_password = CustomResource(
            self,
            "SetTestUserPassword",
            service_token=password_setter_fn.function_arn,
            properties={
                "UserPoolId": user_pool.user_pool_id,
                "Username": "testuser",
                "Password": "TestPassword123!",
            },
        )
        set_password.node.add_dependency(test_user)

        # OIDC Discovery URL for Cognito
        discovery_url = f"https://cognito-idp.{self.region}.amazonaws.com/{user_pool.user_pool_id}/.well-known/openid-configuration"

        # MCP Server Runtime
        mcp_runtime = bedrockagentcore.CfnRuntime(
            self,
            "MCPServerRuntime",
            agent_runtime_name=f"{self.stack_name.replace('-', '_')}_{agent_name.value_as_string}",
            agent_runtime_artifact=bedrockagentcore.CfnRuntime.AgentRuntimeArtifactProperty(
                container_configuration=bedrockagentcore.CfnRuntime.ContainerConfigurationProperty(
                    container_uri=mcp_server_image.image_uri
                )
            ),
            network_configuration=bedrockagentcore.CfnRuntime.NetworkConfigurationProperty(
                network_mode=network_mode.value_as_string
            ),
            protocol_configuration="MCP",
            role_arn=agent_role.role_arn,
            authorizer_configuration=bedrockagentcore.CfnRuntime.AuthorizerConfigurationProperty(
                custom_jwt_authorizer=bedrockagentcore.CfnRuntime.CustomJWTAuthorizerConfigurationProperty(
                    allowed_clients=[user_pool_client.user_pool_client_id],
                    discovery_url=discovery_url,
                )
            ),
            description=f"Spring AI MCP server with elicitation - {self.stack_name}",
            environment_variables={
                "PORT": "8000",
                "AWS_REGION": self.region,
            },
        )
        mcp_runtime.node.add_dependency(set_password)

        # Stack Outputs
        CfnOutput(
            self,
            "MCPServerRuntimeId",
            description="ID of the MCP server runtime",
            value=mcp_runtime.attr_agent_runtime_id,
        )

        CfnOutput(
            self,
            "MCPServerRuntimeArn",
            description="ARN of the MCP server runtime",
            value=mcp_runtime.attr_agent_runtime_arn,
        )

        CfnOutput(
            self,
            "AgentRoleArn",
            description="ARN of the agent execution role",
            value=agent_role.role_arn,
        )

        CfnOutput(
            self,
            "ImageUri",
            description="URI of the Docker image",
            value=mcp_server_image.image_uri,
        )

        CfnOutput(
            self,
            "CognitoUserPoolId",
            description="ID of the Cognito User Pool",
            value=user_pool.user_pool_id,
        )

        CfnOutput(
            self,
            "CognitoClientId",
            description="ID of the Cognito User Pool Client",
            value=user_pool_client.user_pool_client_id,
        )

        CfnOutput(
            self,
            "CognitoDiscoveryUrl",
            description="Cognito OIDC Discovery URL",
            value=discovery_url,
        )

        CfnOutput(
            self,
            "TestUsername",
            description="Test username for authentication",
            value="testuser",
        )

        CfnOutput(
            self,
            "TestPassword",
            description="Test password for authentication",
            value="TestPassword123!",
        )
