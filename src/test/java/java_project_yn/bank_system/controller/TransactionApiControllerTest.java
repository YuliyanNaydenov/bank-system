package java_project_yn.bank_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java_project_yn.bank_system.config.SecurityConfig;
import java_project_yn.bank_system.dto.AmountDTO;
import java_project_yn.bank_system.dto.TransactionDTO;
import java_project_yn.bank_system.service.TransactionService;
import java_project_yn.bank_system.web.api.TransactionApiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionApiController.class)
@Import(SecurityConfig.class)
class TransactionApiControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @Test
    @WithMockUser(authorities = "employee")
    void deposit_asEmployee_returnsCreated() throws Exception {
        given(transactionService.deposit(anyLong(), any(BigDecimal.class)))
                .willReturn(TransactionDTO.builder().id(1L).type("DEPOSIT")
                        .amount(new BigDecimal("100.00")).build());

        mockMvc.perform(post("/api/accounts/1/deposit").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AmountDTO.builder().amount(new BigDecimal("100.00")).build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"));
    }

    @Test
    @WithMockUser(authorities = "client")
    void deposit_asClient_isForbidden() throws Exception {
        mockMvc.perform(post("/api/accounts/1/deposit").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AmountDTO.builder().amount(new BigDecimal("100.00")).build())))
                .andExpect(status().isForbidden());
    }
}
