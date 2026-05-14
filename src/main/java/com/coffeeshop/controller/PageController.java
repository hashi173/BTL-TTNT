package com.coffeeshop.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/about")
    public String about() {
        return "redirect:/#about";
    }

    @GetMapping("/active")
    public String active() {
        return "redirect:/";
    }

    @GetMapping("/info")
    public String info() {
        return "redirect:/#info";
    }
}
