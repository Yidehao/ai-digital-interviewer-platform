package org.interviewer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("hello")
    public Object hello() {
        return "Hello welcome to the AI interviewer project!";
    }

}