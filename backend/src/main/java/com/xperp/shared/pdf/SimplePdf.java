package com.xperp.shared.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Documentos PDF sencillos con OpenPDF: encabezado, pares clave-valor, una tabla y notas. Lo usan
 * las OC (compras) y las guías de despacho (logística).
 */
public final class SimplePdf {

  private static final Color PRIMARY = new Color(0x1f, 0x5f, 0x4a);
  private static final Color MUTED = new Color(0x5b, 0x65, 0x5f);

  private final String title;
  private final String subtitle;
  private final Map<String, String> fields = new LinkedHashMap<>();
  private String[] headers;
  private float[] widths;
  private final List<String[]> rows = new ArrayList<>();
  private final List<String> notes = new ArrayList<>();

  private SimplePdf(String title, String subtitle) {
    this.title = title;
    this.subtitle = subtitle;
  }

  public static SimplePdf of(String title, String subtitle) {
    return new SimplePdf(title, subtitle);
  }

  public SimplePdf field(String label, String value) {
    fields.put(label, value == null ? "—" : value);
    return this;
  }

  public SimplePdf table(String[] headers, float[] widths) {
    this.headers = headers;
    this.widths = widths;
    return this;
  }

  public SimplePdf row(String... cells) {
    rows.add(cells);
    return this;
  }

  public SimplePdf note(String text) {
    notes.add(text);
    return this;
  }

  public byte[] render() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document doc = new Document(PageSize.LETTER, 40, 40, 40, 40);
    PdfWriter.getInstance(doc, out);
    doc.open();
    Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, PRIMARY);
    Font h2 = FontFactory.getFont(FontFactory.HELVETICA, 11, MUTED);
    Font label = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
    Font th = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    Font td = FontFactory.getFont(FontFactory.HELVETICA, 9);

    doc.add(new Paragraph(title, h1));
    doc.add(new Paragraph(subtitle, h2));
    doc.add(new Paragraph(" "));

    PdfPTable info = new PdfPTable(new float[] {1.2f, 3f});
    info.setWidthPercentage(100);
    fields.forEach(
        (k, v) -> {
          PdfPCell kc = new PdfPCell(new Phrase(k, label));
          kc.setBorder(0);
          kc.setPadding(3);
          PdfPCell vc = new PdfPCell(new Phrase(v, body));
          vc.setBorder(0);
          vc.setPadding(3);
          info.addCell(kc);
          info.addCell(vc);
        });
    doc.add(info);
    doc.add(new Paragraph(" "));

    if (headers != null) {
      PdfPTable table = new PdfPTable(widths);
      table.setWidthPercentage(100);
      for (String h : headers) {
        PdfPCell c = new PdfPCell(new Phrase(h, th));
        c.setBackgroundColor(PRIMARY);
        c.setPadding(5);
        table.addCell(c);
      }
      for (String[] r : rows) {
        for (int i = 0; i < r.length; i++) {
          PdfPCell c = new PdfPCell(new Phrase(r[i], td));
          c.setPadding(4);
          if (i > 1) {
            c.setHorizontalAlignment(Element.ALIGN_RIGHT);
          }
          table.addCell(c);
        }
      }
      doc.add(table);
    }

    for (String n : notes) {
      doc.add(new Paragraph(" "));
      doc.add(new Paragraph(n, body));
    }
    doc.close();
    return out.toByteArray();
  }
}
