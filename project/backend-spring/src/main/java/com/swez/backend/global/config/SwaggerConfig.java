/* 
 * Copyright (c) EZ TEAM 
 */
package com.swez.backend.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.swez.backend.global.config.properties.SwaggerProperties;
import com.swez.backend.global.config.properties.SwaggerServerProperties;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/** Swagger(OpenAPI 3) 설정. JWT bearerAuth 를 전역 보안으로 적용한다. */
@Configuration
@EnableConfigurationProperties({SwaggerProperties.class, SwaggerServerProperties.class})
public class SwaggerConfig {

  /** SecurityScheme 이름. Swagger UI 의 Authorize 버튼과 연결되는 식별자다. */
  private static final String BEARER_AUTH = "bearerAuth";

  @Bean
  public OpenAPI openAPI(
      SwaggerProperties swaggerProperties, SwaggerServerProperties serverProperties) {
    return new OpenAPI()
        .info(buildInfo(swaggerProperties))
        .addServersItem(buildServer(serverProperties))
        .components(new Components().addSecuritySchemes(BEARER_AUTH, buildBearerAuth()))
        // 모든 API 엔드포인트에 JWT 인증을 전역 보안 요구사항으로 건다
        .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
  }

  /** API 기본 정보 구성 */
  private Info buildInfo(SwaggerProperties props) {
    return new Info()
        .title(props.getTitle())
        .description(props.getDescription())
        .version(props.getVersion());
  }

  /** 서버 URL 정보 구성 */
  private Server buildServer(SwaggerServerProperties props) {
    return new Server().url(props.getUrl()).description(props.getDescription());
  }

  /** HTTP Bearer(JWT) 인증 스킴 정의 */
  private SecurityScheme buildBearerAuth() {
    return new SecurityScheme()
        .type(SecurityScheme.Type.HTTP)
        .scheme("bearer")
        .bearerFormat("JWT")
        .in(SecurityScheme.In.HEADER);
  }
}
