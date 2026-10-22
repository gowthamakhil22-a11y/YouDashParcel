package com.youdash.controller;

import com.youdash.util.InvoiceTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/invoice")
public class AdminInvoiceController {

    @Autowired
    private PublicController publicController;

    @Autowired
    private InvoiceTokenUtil invoiceTokenUtil;

    @GetMapping(value = "/{lrNumber}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyInvoice(
            @PathVariable String lrNumber,
            @RequestParam(required = false) String t) {

        if (!invoiceTokenUtil.verify(lrNumber, t)) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_HTML)
                    .body("<html><body style='font-family:sans-serif;text-align:center;padding:40px'>" +
                          "<h2 style='color:#dc2626'>Invalid or missing invoice link</h2>" +
                          "<p>Please scan the QR code on the original LR receipt.</p></body></html>");
        }

        return publicController.verifyInvoice(lrNumber);
    }
}
