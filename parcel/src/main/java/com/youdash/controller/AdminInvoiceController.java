package com.youdash.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/invoice")
public class AdminInvoiceController {

    @Autowired
    private PublicController publicController;

    @GetMapping(value = "/{lrNumber}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyInvoice(@PathVariable String lrNumber) {
        return publicController.verifyInvoice(lrNumber);
    }
}
