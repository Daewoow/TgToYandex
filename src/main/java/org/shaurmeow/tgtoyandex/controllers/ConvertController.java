package org.shaurmeow.tgtoyandex.controllers;

import org.shaurmeow.tgtoyandex.repository.ConvertRepository;
import org.shaurmeow.tgtoyandex.services.ConvertService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "convert")
public class ConvertController{

    public final ConvertService convertService;
    public final ConvertRepository convertRepository;

    public ConvertController(ConvertService convertService, ConvertRepository convertRepository) {
        this.convertService = convertService;
        this.convertRepository = convertRepository;
    }

    @GetMapping
    public String convert(){
        return convertService.convert();
    }
}
