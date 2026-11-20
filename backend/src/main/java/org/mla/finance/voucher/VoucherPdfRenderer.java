package org.mla.finance.voucher;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;

/**
 * XHTML -> PDF (openhtmltopdf / PDFBox), en mémoire : rien n'est écrit sur disque (celui de Railway
 * n'est pas persistant, le bon est donc produit à chaque demande). Polices Liberation Sans
 * embarquées (licence SIL OFL, voir resources/pdf/fonts) : le fichier s'affiche à l'identique sur
 * n'importe quel téléphone, y compris ouvert depuis WhatsApp.
 */
@Component
public class VoucherPdfRenderer {

    static final String FONT_FAMILY = "MLA Sans";

    private final byte[] regular = load("pdf/fonts/LiberationSans-Regular.ttf");
    private final byte[] bold = load("pdf/fonts/LiberationSans-Bold.ttf");
    private final String logoDataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(load("pdf/icc-logo.png"));

    public String logoDataUri() {
        return logoDataUri;
    }

    public byte[] render(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> new java.io.ByteArrayInputStream(regular), FONT_FAMILY, 400,
                PdfRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> new java.io.ByteArrayInputStream(bold), FONT_FAMILY, 700,
                PdfRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Génération du bon de décaissement impossible", e);
        }
    }

    private static byte[] load(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Ressource du bon de décaissement introuvable : " + path, e);
        }
    }
}
