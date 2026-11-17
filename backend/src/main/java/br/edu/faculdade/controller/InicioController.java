package br.edu.faculdade.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InicioController {
    @GetMapping("/")
    public String inicio() {
        return "redirect:http://127.0.0.1:4200/";
    }
}
