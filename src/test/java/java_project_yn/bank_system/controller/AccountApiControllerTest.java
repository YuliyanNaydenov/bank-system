package java_project_yn.bank_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java_project_yn.bank_system.config.SecurityConfig;
import java_project_yn.bank_system.dto.AccountDTO;
import java_project_yn.bank_system.dto.CreateAccountDTO;
import java_project_yn.bank_system.exception.BusinessRuleException;
import java_project_yn.bank_system.service.AccountService;
import java_project_yn.bank_system.web.api.AccountApiController;
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

@WebMvcTest(AccountApiController.class)
@Import(SecurityConfig.class)
class AccountApiControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AccountService accountService;

    @Test
    @WithMockUser(authorities = "employee")
    void getAll_asEmployee_returnsList() throws Exception {
        given(accountService.getAllAccounts()).willReturn(List.of(
                AccountDTO.builder().id(1L).iban("BG1").balance(new BigDecimal("100.00"))
                        .status("ACTIVE").ownerName("Иван Петров").build()));

        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].iban").value("BG1"));
    }

    @Test
    @WithMockUser(authorities = "employee")
    void open_asEmployee_returnsCreated() throws Exception {
        given(accountService.openAccount(any())).willReturn(
                AccountDTO.builder().id(1L).iban("BG1").status("ACTIVE").build());

        mockMvc.perform(post("/api/accounts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreateAccountDTO.builder().ownerId(1L).build())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "client")
    void open_asClient_isForbidden() throws Exception {
        mockMvc.perform(post("/api/accounts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreateAccountDTO.builder().ownerId(1L).build())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "employee")
    void open_businessRule_returns422Json() throws Exception {
        given(accountService.openAccount(any()))
                .willThrow(new BusinessRuleException("Началната наличност не може да е отрицателна!"));

        mockMvc.perform(post("/api/accounts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreateAccountDTO.builder().ownerId(1L).build())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }
}
