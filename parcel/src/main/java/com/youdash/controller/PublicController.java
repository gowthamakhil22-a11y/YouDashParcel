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
        String qty = o.getPieceCount() != null ? String.valueOf(o.getPieceCount()) : "1";

        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'/>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'/>" +
                "<title>LR Invoice – " + esc(o.getDisplayOrderId()) + "</title>" +
                "<style>" +
                "*{box-sizing:border-box;margin:0;padding:0}" +
                "body{font-family:Arial,sans-serif;background:#f0f0f0;padding:16px;color:#111}" +
                ".no-print{}" +
                "@media print{.no-print{display:none!important}body{background:#fff;padding:0}.invoice{box-shadow:none;max-width:100%}}" +
                ".invoice{background:#fff;max-width:620px;margin:0 auto;padding:24px;box-shadow:0 2px 16px rgba(0,0,0,.12)}" +
                ".verified-bar{background:#f0fdf4;border:1px solid #86efac;border-radius:6px;padding:8px 14px;display:flex;align-items:center;gap:8px;margin-bottom:18px;font-size:13px;color:#166534;font-weight:600}" +
                ".header{text-align:center;border-bottom:2px solid #111;padding-bottom:12px;margin-bottom:14px}" +
                ".header h1{font-size:17px;letter-spacing:1px;text-transform:uppercase}" +
                ".header p{font-size:11px;color:#555;margin-top:3px}" +
                ".meta{display:flex;justify-content:space-between;margin-bottom:14px;font-size:12px}" +
                ".meta .block{text-align:center}" +
                ".meta .block .lbl{font-size:10px;color:#888;text-transform:uppercase;letter-spacing:.4px}" +
                ".meta .block .val{font-weight:700;font-size:14px;margin-top:2px}" +
                ".meta .block.lr .val{font-size:18px;color:#111}" +
                ".section-title{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#555;border-bottom:1px solid #ddd;padding-bottom:4px;margin-bottom:8px}" +
                ".grid2{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:14px}" +
                ".grid3{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;margin-bottom:14px}" +
                ".field .lbl{font-size:10px;color:#888;text-transform:uppercase;letter-spacing:.4px}" +
                ".field .val{font-size:13px;font-weight:600;margin-top:2px}" +
                ".field .sub{font-size:11px;color:#666;margin-top:1px}" +
                "table{width:100%;border-collapse:collapse;font-size:12px;margin-bottom:14px}" +
                "th{background:#f5f5f5;padding:6px 8px;text-align:left;font-size:10px;text-transform:uppercase;letter-spacing:.4px;border:1px solid #ddd}" +
                "td{padding:7px 8px;border:1px solid #ddd}" +
                "td.right,th.right{text-align:right}" +
                ".totals{margin-left:auto;width:260px;font-size:13px}" +
                ".totals tr td{border:none;padding:3px 6px}" +
                ".totals tr.grand td{font-weight:700;font-size:14px;border-top:2px solid #111;padding-top:6px}" +
                ".footer{margin-top:14px;font-size:10px;color:#888;border-top:1px solid #eee;padding-top:10px;text-align:center}" +
                ".print-btn{display:block;margin:16px auto 0;padding:10px 32px;background:#111;color:#fff;border:none;border-radius:6px;font-size:14px;cursor:pointer;letter-spacing:.3px}" +
                ".print-btn:hover{background:#333}" +
                "</style></head><body>" +
                "<div class='invoice'>" +
                "<div class='verified-bar'>&#10003;&nbsp; Original Invoice – Verified from Server</div>" +
                "<div class='header'>" +
                "<h1>YOUDASH EXPRESS DELIVERY</h1>" +
                "<p>15-7-4, 38 BUS STOP BACK SIDE, OLD GAJUWAKA &nbsp;|&nbsp; GSTIN: 37EJAPM5697C1ZY</p>" +
                "</div>" +
                "<div class='meta'>" +
                "<div class='block lr'><div class='lbl'>LR Number</div><div class='val'>" + esc(o.getDisplayOrderId()) + "</div></div>" +
                "<div class='block'><div class='lbl'>Date</div><div class='val'>" + fmt + "</div></div>" +
                "<div class='block'><div class='lbl'>LR Type</div><div class='val'>" + esc(lrType) + "</div></div>" +
                "<div class='block'><div class='lbl'>From</div><div class='val'>" + esc(nvl(o.getOriginHubCity(), "-")) + "</div></div>" +
                "<div class='block'><div class='lbl'>To</div><div class='val'>" + esc(nvl(o.getDestinationHubCity(), "-")) + "</div></div>" +
                "</div>" +
                "<div class='section-title'>Hub Details</div>" +
                "<div class='grid2' style='margin-bottom:14px'>" +
                "<div class='field'><div class='lbl'>Origin Hub</div><div class='val'>" + esc(nvl(o.getOriginHubName(), "-")) + "</div><div class='sub'>" + esc(nvl(o.getOriginHubCity(), "")) + "</div></div>" +
                "<div class='field'><div class='lbl'>Destination Hub</div><div class='val'>" + esc(nvl(o.getDestinationHubName(), "-")) + "</div><div class='sub'>" + esc(nvl(o.getDestinationHubCity(), "")) + "</div></div>" +
                "</div>" +
                "<div class='section-title'>Sender &amp; Receiver</div>" +
                "<div class='grid2' style='margin-bottom:14px'>" +
                "<div class='field'><div class='lbl'>Sender Name</div><div class='val'>" + esc(nvl(o.getSenderName(), "-")) + "</div><div class='sub'>" + esc(nvl(o.getSenderPhone(), "")) + "</div></div>" +
                "<div class='field'><div class='lbl'>Receiver Name</div><div class='val'>" + esc(nvl(o.getReceiverName(), "-")) + "</div><div class='sub'>" + esc(nvl(o.getReceiverPhone(), "")) + "</div></div>" +
                "</div>" +
                "<div class='section-title'>Item Details</div>" +
                "<table><thead><tr><th>Item Description</th><th class='right'>Qty</th><th class='right'>Weight</th><th class='right'>Freight (Rs.)</th></tr></thead>" +
                "<tbody><tr>" +
                "<td>" + esc(nvl(o.getPackageContents(), nvl(o.getCategoryName(), "Parcel"))) + "</td>" +
                "<td class='right'>" + qty + "</td>" +
                "<td class='right'>" + esc(weight) + "</td>" +
                "<td class='right'>" + freight + "</td>" +
                "</tr></tbody></table>" +
                "<table class='totals'><tbody>" +
                "<tr><td>Freight Charge</td><td class='right'>Rs. " + freight + "</td></tr>" +
                "<tr><td>GST</td><td class='right'>Rs. " + gst + "</td></tr>" +
                "<tr><td>L Charges</td><td class='right'>Rs. " + platformFee + "</td></tr>" +
                "<tr class='grand'><td>Net Amount Payable</td><td class='right'>Rs. " + total + "</td></tr>" +
                "</tbody></table>" +
                "<div class='footer'>YouDash Express &bull; This invoice was fetched directly from our server. Any physical copy that does not match this page has been altered.</div>" +
                "</div>" +
                "<button class='print-btn no-print' onclick='window.print()'>&#128438;&nbsp; Print / Save as PDF</button>" +
                "</body></html>";
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

