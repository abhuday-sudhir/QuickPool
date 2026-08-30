package com.QuickPool.dtos;

import com.QuickPool.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponseDto {
    private UUID id;
    private String phone;
    private String name;
    private String email;
    private BigDecimal ratingAvg;
    /** False until the user has finished registration; the app routes on this. */
    private boolean profileComplete;
    private boolean emailVerified;

    public UserResponseDto(User u) {
        this.id = u.getId();
        this.phone = u.getPhone();
        this.name = u.getName();
        this.email = u.getEmail();
        this.ratingAvg = u.getRatingAvg();
        this.profileComplete = isComplete(u);
        this.emailVerified = Boolean.TRUE.equals(u.getEmailVerified());
    }

    public static boolean isComplete(User u) {
        return u.getName() != null && !u.getName().isBlank()
                && u.getEmail() != null && !u.getEmail().isBlank();
    }
}
