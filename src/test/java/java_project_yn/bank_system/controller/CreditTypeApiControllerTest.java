package java_project_yn.bank_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java_project_yn.bank_system.config.SecurityConfig;
import java_project_yn.bank_system.dto.CreateCreditTypeDTO;
import java_project_yn.bank_system.dto.CreditTypeDTO;
import java_project_yn.bank_system.service.CreditTypeService;
import java_project_yn.bank_system.web.api.CreditTypeApiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CreditTypeApiController.class)
@Import(SecurityConfig.class)
class CreditTypeApiControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CreditTypeService creditTypeService;

    private CreateCreditTypeDTO validDto() {
        return CreateCreditTypeDTO.builder()
                .name("Потребителски").annualInterestRate(new BigDecimal("10.5"))
                .maxAmount(new BigDecimal("50000")).maxTermMonths(84).build();
    }

    @Test
    @WithMockUser(authorities = "employee")
    void getAll_authenticated_returnsList() throws Exception {
        given(creditTypeService.getAllCreditTypes()).willReturn(List.of(
                CreditTypeDTO.builder().id(1L).name("Потребителски").build()));

        mockMvc.perform(get("/api/credit-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Потребителски"));
    }

    @Test
    @WithMockUser(authorities = "admin")
    void create_asAdmin_returnsCreated() throws Exception {
        given(creditTypeService.createCreditType(any())).willReturn(
                CreditTypeDTO.builder().id(1L).name("Потребителски").build());

        mockMvc.perform(post("/api/credit-types").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "employee")
    void create_asEmployee_isForbidden() throws Exception {
        mockMvc.perform(post("/api/credit-types").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isForbidden());
    }
}
