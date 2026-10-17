package com.hacisimsek.common.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void notFoundBecomes404Problem() throws Exception {
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Order not found: 42"));
    }

    @Test
    void businessRuleBecomes409Problem() throws Exception {
        mvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://api.ecommerce.local/problems/business-rule"));
    }

    @Test
    void validationErrorsListEveryField() throws Exception {
        mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void parameterAndBodyValidationShareTheFormat() throws Exception {
        mvc.perform(post("/validated-with-header").header("X-Code", "too-long-value")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void unexpectedErrorsDoNotLeakDetails() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    record Body(@NotBlank String name) {
    }

    @RestController
    static class TestController {

        @GetMapping("/missing")
        void missing() {
            throw new ResourceNotFoundException("Order", 42);
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new BusinessRuleException("Order already shipped");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody Body body) {
        }

        @PostMapping("/validated-with-header")
        void validatedWithHeader(@Valid @RequestBody Body body, @RequestHeader("X-Code") @Size(max = 3) String code) {
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("database password is hunter2");
        }
    }
}
