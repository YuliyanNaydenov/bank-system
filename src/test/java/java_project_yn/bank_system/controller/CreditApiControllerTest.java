package java_project_yn.bank_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java_project_yn.bank_system.config.SecurityConfig;
import java_project_yn.bank_system.dto.CreateCreditDTO;
import java_project_yn.bank_system.dto.CreditDTO;
import java_project_yn.bank_system.service.CreditService;
import java_project_yn.bank_system.web.api.CreditApiController;
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

@WebMvcTest(CreditApiController.class)
@Import(SecurityConfig.class)
class CreditApiControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreditService creditService;

    @Test
    @WithMockUser(authorities = "employee")
    void getAll_asEmployee_returnsList() throws Exception {
        given(creditService.getAllCredits()).willReturn(List.of(
                CreditDTO.builder().id(1L).clientName("Иван Петров").amount(new BigDecimal("10000")).build()));

        mockMvc.perform(get("/api/credits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientName").value("Иван Петров"));
    }

    @Test
    @WithMockUser(authorities = "employee")
    void grant_validBody_returnsCreated() throws Exception {
        CreateCreditDTO dto = CreateCreditDTO.builder()
                .clientId(1L).creditTypeId(1L)
                .amount(new BigDecimal("10000")).termMonths(12).build();
        given(creditService.grantCredit(any(CreateCreditDTO.class)))
                .willReturn(CreditDTO.builder().id(7L).monthlyPayment(new BigDecimal("872.20")).build());

        mockMvc.perform(post("/api/credits").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monthlyPayment").value(872.20));
    }

    @Test
    @WithMockUser(authorities = "client")
    void grant_asClient_isForbidden() throws Exception {
        CreateCreditDTO dto = CreateCreditDTO.builder()
                .clientId(1L).creditTypeId(1L)
                .amount(new BigDecimal("10000")).termMonths(12).build();

        mockMvc.perform(post("/api/credits").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }
}
