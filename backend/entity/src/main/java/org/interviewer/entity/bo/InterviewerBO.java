package org.interviewer.entity.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class InterviewerBO {
    private String id;

    @NotBlank(message = "Digital interviewer name cannot be empty")
    private String aiName;

    @NotBlank(message = "Digital interviewer image cannot be empty")
    private String image;

}