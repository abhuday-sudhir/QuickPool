package com.QuickPool.controller;

import com.QuickPool.entity.EmergencyContact;
import com.QuickPool.repository.EmergencyContactRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** This controller talks to its repository directly, so the create-vs-update logic lives here. */
@WebMvcTest(EmergencyContactController.class)
class EmergencyContactControllerTest extends ControllerTestSupport {

    private static final String PATH = "/api/v1/users/me/emergency-contact";

    @MockitoBean private EmergencyContactRepository repository;

    private EmergencyContact existing() {
        EmergencyContact c = new EmergencyContact();
        c.setId(UUID.randomUUID());
        c.setUserId(userId);
        c.setName("Mum");
        c.setPhone("+919000000009");
        c.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        return c;
    }

    @Test
    @DisplayName("GET returns the saved contact")
    void getReturnsContact() throws Exception {
        when(repository.findByUserId(userId)).thenReturn(Optional.of(existing()));

        mvc.perform(get(PATH).with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mum"))
                .andExpect(jsonPath("$.phone").value("+919000000009"));
    }

    @Test
    @DisplayName("GET with no contact saved is an empty 200, not a 404")
    void getNone() throws Exception {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());

        mvc.perform(get(PATH).with(authed()))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("PUT creates a contact for a user who has none, trimming input")
    void createNew() throws Exception {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());

        mvc.perform(put(PATH).with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  Dad ", "phone": "+919000000008"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dad"))
                .andExpect(jsonPath("$.phone").value("+919000000008"));

        ArgumentCaptor<EmergencyContact> saved = ArgumentCaptor.forClass(EmergencyContact.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getName()).isEqualTo("Dad");
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
        assertThat(saved.getValue().getUpdatedAt()).isEqualTo(saved.getValue().getCreatedAt());
    }

    @Test
    @DisplayName("PUT overwrites the existing contact instead of adding a second one")
    void updateExisting() throws Exception {
        EmergencyContact contact = existing();
        LocalDateTime created = contact.getCreatedAt();
        when(repository.findByUserId(userId)).thenReturn(Optional.of(contact));

        mvc.perform(put(PATH).with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sister", "phone": "+919000000007"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sister"));

        verify(repository).save(contact);
        assertThat(contact.getName()).isEqualTo("Sister");
        assertThat(contact.getPhone()).isEqualTo("+919000000007");
        assertThat(contact.getCreatedAt()).isEqualTo(created);
        assertThat(contact.getUpdatedAt()).isAfter(created);
    }

    @Test
    @DisplayName("PUT with a blank name and a local-format phone fails validation")
    void saveInvalid() throws Exception {
        mvc.perform(put(PATH).with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "phone": "09000000007"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Please enter a name"))
                .andExpect(jsonPath("$.fieldErrors.phone").value("Use international format, e.g. +919000000001"));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("DELETE removes the contact when there is one")
    void deleteExisting() throws Exception {
        EmergencyContact contact = existing();
        when(repository.findByUserId(userId)).thenReturn(Optional.of(contact));

        mvc.perform(delete(PATH).with(authed()))
                .andExpect(status().isOk());

        verify(repository).delete(contact);
    }

    @Test
    @DisplayName("DELETE with nothing saved is a harmless no-op")
    void deleteNone() throws Exception {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());

        mvc.perform(delete(PATH).with(authed()))
                .andExpect(status().isOk());

        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("the contact is private to its owner, so anonymous access is a 401")
    void unauthenticated() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(repository);
    }
}
