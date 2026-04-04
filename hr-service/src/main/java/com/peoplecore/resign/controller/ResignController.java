package com.peoplecore.resign.controller;

import com.peoplecore.resign.service.ResignService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/resign")
public class ResignController {

    private final ResignService resignService;


    public ResignController(ResignService resignService) {
        this.resignService = resignService;
    }


}
