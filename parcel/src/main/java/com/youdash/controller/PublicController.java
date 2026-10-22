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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
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
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .body(buildNotFoundHtml(lrNumber));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
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
        String desc = esc(nvl(o.getPackageContents(), nvl(o.getCategoryName(), "Parcel")));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='en'><head>")
          .append("<meta charset='UTF-8'/>")
          .append("<meta name='viewport' content='width=device-width,initial-scale=1'/>")
          .append("<title>LR Invoice – ").append(esc(o.getDisplayOrderId())).append("</title>")
          .append("<style>")
          .append("*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}")
          .append("body{font-family:Arial,Helvetica,sans-serif;background:#f3f4f6;padding:12px;color:#1a1a1a;min-height:100vh;-webkit-text-size-adjust:100%}")
          // Invoice wrapper
          .append(".inv{background:#fff;max-width:680px;margin:0 auto;border-radius:8px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.13)}")
          // Header
          .append(".inv-hdr{background:#c0392b;color:#fff;padding:18px 22px;display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}")
          .append(".inv-hdr-left h1{font-size:18px;font-weight:800;letter-spacing:1px;text-transform:uppercase;line-height:1.2}")
          .append(".inv-hdr-left p{font-size:10px;opacity:.85;margin-top:3px;line-height:1.4}")
          .append(".inv-hdr-right{text-align:right;flex-shrink:0}")
          .append(".inv-hdr-right .gstin-lbl{font-size:9px;opacity:.7;text-transform:uppercase;letter-spacing:.5px}")
          .append(".inv-hdr-right .gstin-val{font-size:13px;font-weight:700;margin-top:2px}")
          // Verified bar
          .append(".vbar{background:#f0fdf4;border-bottom:1px solid #bbf7d0;padding:8px 20px;display:flex;align-items:center;gap:8px;font-size:12px;color:#15803d;font-weight:600}")
          .append(".vbar-icon{width:18px;height:18px;background:#22c55e;border-radius:50%;color:#fff;display:inline-flex;align-items:center;justify-content:center;font-size:11px;flex-shrink:0;font-weight:700}")
          // Body
          .append(".inv-body{padding:18px 22px}")
          // LR strip
          .append(".lr-strip{display:grid;grid-template-columns:repeat(5,1fr);gap:6px;background:#fafafa;border:1px solid #e5e7eb;border-radius:6px;padding:11px 14px;margin-bottom:18px}")
          .append(".lr-item{text-align:center;min-width:0}")
          .append(".lr-lbl{font-size:9px;text-transform:uppercase;letter-spacing:.5px;color:#9ca3af;margin-bottom:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}")
          .append(".lr-val{font-size:13px;font-weight:700;color:#111;word-break:break-word}")
          .append(".lr-val.lr-num{font-size:15px;color:#c0392b}")
          // Section title
          .append(".sec-title{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.7px;color:#6b7280;background:#f9fafb;padding:5px 10px;border-left:3px solid #c0392b;margin-bottom:10px;border-radius:0 4px 4px 0}")
          // Grid
          .append(".g2{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:16px}")
          // Field card
          .append(".fc{background:#f9fafb;border:1px solid #e5e7eb;border-radius:6px;padding:9px 11px;min-width:0}")
          .append(".fc-lbl{font-size:9px;text-transform:uppercase;letter-spacing:.5px;color:#9ca3af;margin-bottom:3px}")
          .append(".fc-val{font-size:13px;font-weight:600;color:#111;word-break:break-word}")
          .append(".fc-sub{font-size:11px;color:#6b7280;margin-top:2px;word-break:break-word}")
          // Table
          .append("table{width:100%;border-collapse:collapse;font-size:12px;margin-bottom:0}")
          .append("thead tr{background:#f3f4f6}")
          .append("th{padding:8px 10px;text-align:left;font-size:9px;text-transform:uppercase;letter-spacing:.5px;color:#6b7280;font-weight:700;border-bottom:2px solid #e5e7eb;white-space:nowrap}")
          .append("td{padding:10px;border-bottom:1px solid #f3f4f6;color:#374151;word-break:break-word}")
          .append("tbody tr:last-child td{border-bottom:none}")
          .append(".r{text-align:right}")
          // Totals
          .append(".tot-wrap{display:flex;justify-content:flex-end;margin-top:14px}")
          .append(".tot{min-width:230px;border:1px solid #e5e7eb;border-radius:6px;overflow:hidden;width:100%;max-width:280px}")
          .append(".tot-row{display:flex;justify-content:space-between;padding:7px 12px;font-size:12px;color:#374151;border-bottom:1px solid #f3f4f6;gap:8px}")
          .append(".tot-row:last-child{border-bottom:none}")
          .append(".tot-row span:last-child{font-weight:600;white-space:nowrap}")
          .append(".tot-row.grand{background:#1a1a1a;color:#fff;font-weight:700;font-size:13px}")
          .append(".tot-row.grand span:last-child{font-weight:800}")
          // Footer
          .append(".inv-ftr{background:#f9fafb;border-top:1px solid #e5e7eb;padding:11px 22px;font-size:10px;color:#9ca3af;text-align:center;line-height:1.5}")
          // Print button
          .append(".print-btn{display:block;margin:12px auto 0;padding:12px;background:#c0392b;color:#fff;border:none;border-radius:0 0 8px 8px;font-size:14px;font-weight:600;cursor:pointer;letter-spacing:.3px;width:100%;max-width:680px}")
          .append(".print-btn:hover{background:#a93226}")
          // Mobile
          .append("@media(max-width:520px){")
          .append("body{padding:0;background:#fff}")
          .append(".inv{border-radius:0;box-shadow:none}")
          .append(".inv-hdr{padding:14px 16px}")
          .append(".inv-hdr-left h1{font-size:15px}")
          .append(".inv-hdr-right{display:none}")
          .append(".inv-body{padding:14px 16px}")
          .append(".lr-strip{grid-template-columns:repeat(3,1fr)}")
          .append(".lr-item:nth-child(4),.lr-item:nth-child(5){grid-column:span 1}")
          .append(".g2{grid-template-columns:1fr}")
          .append(".tot-wrap{justify-content:stretch}")
          .append(".tot{max-width:100%}")
          .append(".print-btn{border-radius:0;margin-top:0}")
          .append("}")
          // Print
          .append("@media print{")
          .append(".no-print{display:none!important}")
          .append("body{background:#fff;padding:0}")
          .append(".inv{box-shadow:none;border-radius:0;max-width:100%}")
          .append(".inv-hdr,.tot-row.grand{-webkit-print-color-adjust:exact;print-color-adjust:exact}")
          .append("}")
          .append("</style></head><body>")
          // Invoice
          .append("<div class='inv'>")
          // Header
          .append("<div class='inv-hdr'>")
          .append("<div class='inv-hdr-left'>")
          .append("<h1>YouDash Express</h1>")
          .append("<p>15-7-4, 38 Bus Stop Back Side, Old Gajuwaka, Visakhapatnam</p>")
          .append("</div>")
          .append("<div class='inv-hdr-right'>")
          .append("<div class='gstin-lbl'>GSTIN</div>")
          .append("<div class='gstin-val'>37EJAPM5697C1ZY</div>")
          .append("</div>")
          .append("</div>")
          // Verified bar
          .append("<div class='vbar'><span class='vbar-icon'>&#10003;</span>Original Invoice &ndash; Verified directly from server</div>")
          // Body
          .append("<div class='inv-body'>")
          // LR strip
          .append("<div class='lr-strip'>")
          .append("<div class='lr-item'><div class='lr-lbl'>LR Number</div><div class='lr-val lr-num'>").append(esc(o.getDisplayOrderId())).append("</div></div>")
          .append("<div class='lr-item'><div class='lr-lbl'>Date</div><div class='lr-val'>").append(fmt).append("</div></div>")
          .append("<div class='lr-item'><div class='lr-lbl'>LR Type</div><div class='lr-val'>").append(esc(lrType)).append("</div></div>")
          .append("<div class='lr-item'><div class='lr-lbl'>From</div><div class='lr-val'>").append(esc(nvl(o.getOriginHubCity(), "-"))).append("</div></div>")
          .append("<div class='lr-item'><div class='lr-lbl'>To</div><div class='lr-val'>").append(esc(nvl(o.getDestinationHubCity(), "-"))).append("</div></div>")
          .append("</div>")
          // Hub details
          .append("<div class='sec-title'>Hub Details</div>")
          .append("<div class='g2'>")
          .append("<div class='fc'><div class='fc-lbl'>Origin Hub</div><div class='fc-val'>").append(esc(nvl(o.getOriginHubName(), "-"))).append("</div><div class='fc-sub'>").append(esc(nvl(o.getOriginHubCity(), ""))).append("</div></div>")
          .append("<div class='fc'><div class='fc-lbl'>Destination Hub</div><div class='fc-val'>").append(esc(nvl(o.getDestinationHubName(), "-"))).append("</div><div class='fc-sub'>").append(esc(nvl(o.getDestinationHubCity(), ""))).append("</div></div>")
          .append("</div>")
          // Sender & Receiver
          .append("<div class='sec-title'>Sender &amp; Receiver</div>")
          .append("<div class='g2'>")
          .append("<div class='fc'><div class='fc-lbl'>Sender</div><div class='fc-val'>").append(esc(nvl(o.getSenderName(), "-"))).append("</div><div class='fc-sub'>").append(esc(nvl(o.getSenderPhone(), ""))).append("</div></div>")
          .append("<div class='fc'><div class='fc-lbl'>Receiver</div><div class='fc-val'>").append(esc(nvl(o.getReceiverName(), "-"))).append("</div><div class='fc-sub'>").append(esc(nvl(o.getReceiverPhone(), ""))).append("</div></div>")
          .append("</div>")
          // Item details
          .append("<div class='sec-title'>Item Details</div>")
          .append("<table><thead><tr>")
          .append("<th>Description</th><th class='r'>Qty</th><th class='r'>Weight</th><th class='r'>Freight (Rs.)</th>")
          .append("</tr></thead><tbody><tr>")
          .append("<td>").append(desc).append("</td>")
          .append("<td class='r'>").append(qty).append("</td>")
          .append("<td class='r'>").append(esc(weight)).append("</td>")
          .append("<td class='r'>").append(freight).append("</td>")
          .append("</tr></tbody></table>")
          // Totals
          .append("<div class='tot-wrap'><div class='tot'>")
          .append("<div class='tot-row'><span>Freight Charge</span><span>Rs. ").append(freight).append("</span></div>")
          .append("<div class='tot-row'><span>GST</span><span>Rs. ").append(gst).append("</span></div>")
          .append("<div class='tot-row'><span>L Charges</span><span>Rs. ").append(platformFee).append("</span></div>")
          .append("<div class='tot-row grand'><span>Net Payable</span><span>Rs. ").append(total).append("</span></div>")
          .append("</div></div>")
          .append("</div>")
          // Footer
          .append("<div class='inv-ftr'>YouDash Express &bull; This invoice was fetched directly from our server. Any physical copy that does not match this page has been altered.</div>")
          .append("</div>")
          // Print button
          .append("<button class='print-btn no-print' onclick='window.print()'>&#128438;&nbsp; Print / Save as PDF</button>")
          .append("</body></html>");
        return sb.toString();
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

