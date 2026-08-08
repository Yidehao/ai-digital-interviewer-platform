package org.interviewer.entity.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.aspectj.bridge.MessageWriter;
import org.hibernate.validator.constraints.Length;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class VerifySMSBO {

    @NotBlank(message = "Mobile number cannot be empty")
    @Length(message = "Mobile number length is incorrect", min = 10, max = 11)
    private String mobile;

    @NotBlank(message = "Verification code cannot be empty")
    private String smsCode;

}
