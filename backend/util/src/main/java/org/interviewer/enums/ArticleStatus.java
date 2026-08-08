package org.interviewer.enums;

/**
 * Article review status enum
 *        Article status:
 *          0: Closed, pending publication
 *          1: Normal, viewable
 *          2: Deleted, cannot view
 */
public enum ArticleStatus {
    CLOSE(0, "Closed, pending publication"),
    OPEN(1, "Normal, viewable"),
    DELETE(2, "Deleted, cannot view");

    public final Integer type;
    public final String value;

    ArticleStatus(Integer type, String value) {
        this.type = type;
        this.value = value;
    }

}
