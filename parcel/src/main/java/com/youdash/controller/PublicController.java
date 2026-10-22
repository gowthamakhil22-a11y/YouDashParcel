package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.BannerDTO;
import com.youdash.dto.CheckoutPaymentOptionsDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.PackageCategoryDTO;
import com.youdash.dto.PublicZoneOptionDTO;
import com.youdash.dto.VehicleDTO;
import com.youdash.dto.VersionCheckResponseDTO;
import com.youdash.service.AdminService;
import com.youdash.service.AppConfigService;
import com.youdash.service.AppVersionService;
import com.youdash.service.BannerService;
import com.youdash.service.OrderService;
import com.youdash.service.PackageCategoryService;
import com.youdash.service.ZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/public")
@Tag(name = "Public", description = "Public app bootstrapping data (no JWT).")
public class PublicController {

    @GetMapping("/health")
    @Operation(summary = "Backend health", description = "Lightweight liveness check for mobile apps (no JWT).")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @Autowired
    private AdminService adminService;

    @Autowired
    private PackageCategoryService packageCategoryService;

    @Autowired
    private AppConfigService appConfigService;

    @Autowired
    private BannerService bannerService;

    @Autowired
    private AppVersionService appVersionService;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private OrderService orderService;

    @GetMapping("/vehicles")
    @Operation(summary = "List active vehicles")
    public ApiResponse<List<VehicleDTO>> getActiveVehicles() {
        return adminService.getActiveVehicles();
    }

    @GetMapping("/zones")
    @Operation(summary = "List active zones", description = "Used by rider app signup to select primary service zone.")
    public ApiResponse<List<PublicZoneOptionDTO>> getActiveZones() {
        return zoneService.listActivePublic();
    }

    @GetMapping("/package-categories")
    @Operation(summary = "List active package categories")
    public ApiResponse<List<PackageCategoryDTO>> packageCategories() {
        return packageCategoryService.listActivePublic();
    }

    @GetMapping("/checkout-payment-options")
    @Operation(summary = "Get checkout payment options", description = "Used by user app to render COD/ONLINE selectors based on live admin config.")
    public ApiResponse<CheckoutPaymentOptionsDTO> checkoutPaymentOptions() {
        return appConfigService.getCheckoutPaymentOptions();
    }

    @GetMapping("/banners")
    @Operation(summary = "Get active user banners", description = "Returns active banners sorted by sortOrder for user-home carousel.")
    public ApiResponse<List<BannerDTO>> banners() {
        return bannerService.listPublicActive();
    }

    @GetMapping("/version-check")
    @Operation(summary = "Check if app update is required", description = "Returns updateRequired=true if app versionCode does not match the admin-configured code.")
    public ApiResponse<VersionCheckResponseDTO> versionCheck(
            @RequestParam String appType,
            @RequestParam Integer versionCode) {
        return appVersionService.checkVersion(appType, versionCode);
    }

    @GetMapping(value = "/invoice/{lrNumber}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Verify H2H invoice", description = "Public invoice verification page — scan QR on the LR to confirm authenticity. No authentication required.")
    public ResponseEntity<String> verifyInvoice(@PathVariable String lrNumber) {
        ApiResponse<OrderResponseDTO> resp = orderService.getOrderByRef(lrNumber.trim());
        if (!Boolean.TRUE.equals(resp.getSuccess()) || resp.getData() == null) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildNotFoundHtml(lrNumber));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(buildInvoiceHtml(resp.getData()));
    }

    private String buildInvoiceHtml(OrderResponseDTO o) {
        String fmt = o.getCreatedAt() != null ? o.getCreatedAt().substring(0, 10).replace("-", "/") : "-";
        String lrType = resolvePaymentLabel(o);
        String freight = formatInr(o.getSubtotal());
        String gst = formatInr(o.getGstAmount());
        String platformFee = formatInr(o.getPlatformFee());
        String total = formatInr(o.getTotalAmount());
        String weight = o.getWeight() != null ? o.getWeight() + " " + nvl(o.getWeightUnit(), "KG") : "-";

        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'/>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'/>" +
                "<title>Invoice Verification – " + esc(o.getDisplayOrderId()) + "</title>" +
                "<style>" +
                "body{font-family:sans-serif;background:#f5f5f5;margin:0;padding:16px;color:#111}" +
                ".card{background:#fff;border-radius:10px;padding:20px;max-width:480px;margin:0 auto;box-shadow:0 2px 12px rgba(0,0,0,.1)}" +
                ".badge{display:inline-block;background:#16a34a;color:#fff;font-size:12px;padding:3px 10px;border-radius:20px;margin-bottom:12px}" +
                "h2{margin:0 0 4px;font-size:18px}" +
                ".sub{color:#666;font-size:13px;margin-bottom:16px}" +
                ".section{margin-bottom:14px}" +
                ".label{font-size:11px;color:#888;text-transform:uppercase;letter-spacing:.5px}" +
                ".value{font-size:14px;font-weight:600;margin-top:2px}" +
                ".row{display:flex;gap:12px;margin-bottom:10px}" +
                ".row .col{flex:1}" +
                ".divider{border:none;border-top:1px solid #eee;margin:14px 0}" +
                ".amount-row{display:flex;justify-content:space-between;font-size:13px;padding:4px 0}" +
                ".amount-row.total{font-weight:700;font-size:15px;border-top:1px solid #eee;padding-top:8px;margin-top:4px}" +
                ".verified{background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px;padding:10px 14px;display:flex;align-items:center;gap:8px;margin-bottom:16px}" +
                ".verified svg{flex-shrink:0}" +
                ".co{font-size:11px;color:#aaa;text-align:center;margin-top:16px}" +
                "</style></head><body>" +
                "<div class='card'>" +
                "<div class='badge'>&#10003; VERIFIED – Original Invoice</div>" +
                "<h2>YouDash Express</h2>" +
                "<div class='sub'>LR No: <strong>" + esc(o.getDisplayOrderId()) + "</strong> &nbsp;|&nbsp; " + fmt + " &nbsp;|&nbsp; " + esc(lrType) + "</div>" +
                "<div class='section'>" +
                "<div class='row'>" +
                "<div class='col'><div class='label'>From Hub</div><div class='value'>" + esc(nvl(o.getOriginHubName(), "-")) + "</div><div style='font-size:12px;color:#666'>" + esc(nvl(o.getOriginHubCity(), "")) + "</div></div>" +
                "<div class='col'><div class='label'>To Hub</div><div class='value'>" + esc(nvl(o.getDestinationHubName(), "-")) + "</div><div style='font-size:12px;color:#666'>" + esc(nvl(o.getDestinationHubCity(), "")) + "</div></div>" +
                "</div></div>" +
                "<hr class='divider'/>" +
                "<div class='section'>" +
                "<div class='row'>" +
                "<div class='col'><div class='label'>Sender</div><div class='value'>" + esc(nvl(o.getSenderName(), "-")) + "</div><div style='font-size:12px;color:#666'>" + esc(nvl(o.getSenderPhone(), "")) + "</div></div>" +
                "<div class='col'><div class='label'>Receiver</div><div class='value'>" + esc(nvl(o.getReceiverName(), "-")) + "</div><div style='font-size:12px;color:#666'>" + esc(nvl(o.getReceiverPhone(), "")) + "</div></div>" +
                "</div></div>" +
                "<hr class='divider'/>" +
                "<div class='section'>" +
                "<div class='row'>" +
                "<div class='col'><div class='label'>Category</div><div class='value'>" + esc(nvl(o.getCategoryName(), "-")) + "</div></div>" +
                "<div class='col'><div class='label'>Weight</div><div class='value'>" + esc(weight) + "</div></div>" +
                "<div class='col'><div class='label'>Qty</div><div class='value'>" + nvl(o.getPieceCount() != null ? String.valueOf(o.getPieceCount()) : null, "1") + "</div></div>" +
                "</div></div>" +
                "<hr class='divider'/>" +
                "<div class='section'>" +
                "<div class='amount-row'><span>Freight Charge</span><span>Rs. " + freight + "</span></div>" +
                "<div class='amount-row'><span>GST</span><span>Rs. " + gst + "</span></div>" +
                "<div class='amount-row'><span>L Charges</span><span>Rs. " + platformFee + "</span></div>" +
                "<div class='amount-row total'><span>Net Amount Payable</span><span>Rs. " + total + "</span></div>" +
                "</div>" +
                "<div class='co'>YouDash Express &bull; This page confirms the original unmodified invoice.</div>" +
                "</div></body></html>";
    }

    private String buildNotFoundHtml(String lrNumber) {
        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'/>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'/>" +
                "<title>Invoice Not Found</title>" +
                "<style>body{font-family:sans-serif;background:#f5f5f5;margin:0;padding:16px;color:#111}" +
                ".card{background:#fff;border-radius:10px;padding:32px 20px;max-width:400px;margin:40px auto;text-align:center;box-shadow:0 2px 12px rgba(0,0,0,.1)}" +
                "h2{color:#dc2626;margin:0 0 8px}p{color:#666;font-size:14px}</style></head><body>" +
                "<div class='card'><h2>Invoice Not Found</h2>" +
                "<p>No invoice found for LR number <strong>" + esc(lrNumber) + "</strong>.</p>" +
                "<p>This QR code may be invalid or the invoice does not exist.</p></div></body></html>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String nvl(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v : fallback;
    }

    private static String formatInr(Double v) {
        if (v == null) return "0.00";
        return String.format("%.2f", v);
    }

    private static String resolvePaymentLabel(OrderResponseDTO o) {
        String ps = o.getPaymentStatus() != null ? o.getPaymentStatus().toUpperCase() : "";
        if (ps.equals("TO_PAY") || ps.equals("TOPAY")) return "To Pay";
        if (ps.equals("PAID")) return "Paid";
        if (o.getPaymentType() != null) {
            String pt = o.getPaymentType().name().toUpperCase();
            if (pt.equals("TO_PAY") || pt.equals("TOPAY")) return "To Pay";
            if (pt.startsWith("COD")) return "COD";
        }
        return "Paid";
    }
}

