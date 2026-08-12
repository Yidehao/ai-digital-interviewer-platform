package org.interviewer.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
/**
 * Questions as sent to the candidate's device.
 *
 * Deliberately has no referenceAnswer field: this VO crosses the wire to the candidate,
 * and shipping the model answer to the person being tested defeats the interview.
 * Reference answers are resolved server-side at grading time.
 */
public class InitQuestionsVO {

    private String id;
    private String question;
    private String aiSrc;
    private String interviewerId;

}
