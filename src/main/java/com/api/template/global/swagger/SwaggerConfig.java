package com.api.template.global.swagger;

import com.api.template.global.response.ErrorResponse;
import com.api.template.global.response.code.BaseResponseCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class SwaggerConfig {

    private static final String JWT_SCHEME_NAME = "JWT Authentication";

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("Spring Boot API Template")
                .description("Spring Boot REST API boilerplate")
                .version("v1");

        SecurityRequirement securityRequirement = new SecurityRequirement().addList(JWT_SCHEME_NAME);
        Components components = new Components()
                .addSecuritySchemes(JWT_SCHEME_NAME, new SecurityScheme()
                        .name(JWT_SCHEME_NAME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        return new OpenAPI()
                .info(info)
                .addSecurityItem(securityRequirement)
                .components(components);
    }

    @Bean
    public OperationCustomizer errorCodeExampleCustomizer() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            ApiErrorCodeExamples[] annotations = handlerMethod.getMethod()
                    .getAnnotationsByType(ApiErrorCodeExamples.class);
            if (annotations.length == 0) {
                return operation;
            }

            BaseResponseCode[] errorCodes = Arrays.stream(annotations)
                    .flatMap(annotation -> resolveErrorCodes(annotation).stream())
                    .toArray(BaseResponseCode[]::new);

            generateErrorCodeResponseExamples(operation, errorCodes);
            return operation;
        };
    }

    private List<BaseResponseCode> resolveErrorCodes(ApiErrorCodeExamples annotation) {
        Class<? extends BaseResponseCode> errorCodeClass = annotation.value();
        if (!errorCodeClass.isEnum()) {
            throw new IllegalArgumentException("@ApiErrorCodeExamples value must be an enum type.");
        }

        List<BaseResponseCode> allCodes = Arrays.asList(errorCodeClass.getEnumConstants());
        Set<String> selectedCodes = Arrays.stream(annotation.codes())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (selectedCodes.isEmpty()) {
            return allCodes;
        }

        List<BaseResponseCode> matchedCodes = allCodes.stream()
                .filter(errorCode -> selectedCodes.contains(getEnumName(errorCode))
                        || selectedCodes.contains(errorCode.getCode()))
                .toList();

        Set<String> matchedIdentifiers = matchedCodes.stream()
                .flatMap(errorCode -> java.util.stream.Stream.of(getEnumName(errorCode), errorCode.getCode()))
                .collect(Collectors.toSet());
        List<String> unknownCodes = selectedCodes.stream()
                .filter(code -> !matchedIdentifiers.contains(code))
                .toList();

        if (!unknownCodes.isEmpty()) {
            throw new IllegalArgumentException("Unknown swagger error code examples: " + unknownCodes);
        }

        return matchedCodes;
    }

    private void generateErrorCodeResponseExamples(Operation operation, BaseResponseCode[] errorCodes) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        Map<Integer, List<ExampleHolder>> statusWithExamples = Arrays.stream(errorCodes)
                .map(this::getSwaggerExample)
                .collect(Collectors.groupingBy(ExampleHolder::getCode));

        addExamplesToResponses(responses, statusWithExamples);
    }

    private ExampleHolder getSwaggerExample(BaseResponseCode errorCode) {
        Example example = new Example();
        example.setValue(ErrorResponse.from(errorCode));

        return ExampleHolder.builder()
                .holder(example)
                .code(errorCode.getHttpStatus())
                .name(getEnumName(errorCode))
                .build();
    }

    private void addExamplesToResponses(
            ApiResponses responses,
            Map<Integer, List<ExampleHolder>> statusWithExamples) {
        statusWithExamples.forEach((status, examples) -> {
            ApiResponse apiResponse = responses.get(String.valueOf(status));
            if (apiResponse == null) {
                apiResponse = new ApiResponse();
                responses.addApiResponse(String.valueOf(status), apiResponse);
            }

            Content content = apiResponse.getContent();
            if (content == null) {
                content = new Content();
                apiResponse.setContent(content);
            }

            MediaType mediaType = content.get("application/json");
            if (mediaType == null) {
                mediaType = new MediaType();
                content.addMediaType("application/json", mediaType);
            }

            for (ExampleHolder example : examples) {
                mediaType.addExamples(example.getName(), example.getHolder());
            }
        });
    }

    private String getEnumName(BaseResponseCode errorCode) {
        return ((Enum<?>) errorCode).name();
    }
}
