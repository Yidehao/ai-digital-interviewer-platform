package org.interviewer.entity.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class AnswerBO {

    private String id;
    private String question;
    private String referenceAnswer;
    private String aiSrc;
    private String interviewerId;
    private String answerContent;

}
