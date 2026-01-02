dependencies {
    implementation("org.springframework.ai:spring-ai-starter-model-bedrock-converse")
    implementation("org.springframework.ai:spring-ai-starter-mcp-client-webflux")
    implementation("software.amazon.awssdk:sso:2.36.3")
    implementation("software.amazon.awssdk:ssooidc:2.36.3")
    // Cognito SDK for JWT token retrieval
    implementation("software.amazon.awssdk:cognitoidentityprovider:2.36.3")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    standardInput = System.`in`
}
