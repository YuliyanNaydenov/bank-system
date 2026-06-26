package java_project_yn.bank_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java_project_yn.bank_system.config.SecurityConfig;
import java_project_yn.bank_system.dto.ClientDTO;
import java_project_yn.bank_system.dto.CreateIndividualClientDTO;
import java_project_yn.bank_system.exception.ClientNotFoundException;
import java_project_yn.bank_system.service.ClientService;
import java_project_yn.bank_system.web.api.ClientApiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientApiController.class)
@Import(SecurityConfig.class)
class ClientApiControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ClientService clientService;

    @Test
    @WithMockUser(authorities = "employee")
    void getAll_asEmployee_returnsList() throws Exception {
        given(clientService.getAllClients()).willReturn(List.of(
                ClientDTO.builder().id(1L).displayName("Иван Петров").identifier("8001011234")
                        .clientType("INDIVIDUAL").build()));

        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Иван Петров"));
    }

    @Test
    @WithMockUser(authorities = "employee")
    void createIndividual_asEmployee_returnsCreated() throws Exception {
        CreateIndividualClientDTO dto = CreateIndividualClientDTO.builder()
                .firstName("Иван").lastName("Петров").egn("8001011234").build();
        given(clientService.createIndividual(any())).willReturn(
                ClientDTO.builder().id(1L).displayName("Иван Петров").build());

        mockMvc.perform(post("/api/clients/individual").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "employee")
    void delete_asEmployee_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/clients/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "employee")
    void getById_notFound_returns404Json() throws Exception {
        given(clientService.getClientById(anyLong()))
                .willThrow(new ClientNotFoundException("Клиент с id=99 не е намерен!"));

        mockMvc.perform(get("/api/clients/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").exists());
    }
}
