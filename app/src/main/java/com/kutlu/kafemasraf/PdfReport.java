package com.kutlu.kafemasraf;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.NumberFormat;
import java.util.Locale;

/** Masraf özetini A4 PDF olarak çizer. */
class PdfReport {
    private static final int W = 595, H = 842, M = 40;
    private static final int BROWN = Color.rgb(0x7A, 0x4A, 0x24);
    private static final int INK = Color.rgb(0x1F, 0x1D, 0x1A);
    private static final int MUTE = Color.rgb(0x6F, 0x6A, 0x62);
    private static final int LINE = Color.rgb(0xE6, 0xE1, 0xD8);
    private static final int TRACK = Color.rgb(0xF0, 0xED, 0xE7);
    private static final int BLUE = Color.rgb(0x25, 0x7D, 0xE0);
    private static final int GREEN = Color.rgb(0x1D, 0x7A, 0x46);
    private static final int RED = Color.rgb(0xB2, 0x3A, 0x2B);
    private static final int[] KIND = {Color.rgb(0x25, 0x7D, 0xE0), Color.rgb(0xF4, 0x72, 0x30), Color.rgb(0x00, 0xB8, 0x94)};

    private final PdfDocument doc = new PdfDocument();
    private PdfDocument.Page page;
    private Canvas c;
    private float y;
    private int pageNo = 0;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final NumberFormat nf2, nf0;
    private String dateStr = "";

    PdfReport() {
        Locale tr = new Locale("tr", "TR");
        nf2 = NumberFormat.getNumberInstance(tr);
        nf2.setMinimumFractionDigits(2);
        nf2.setMaximumFractionDigits(2);
        nf0 = NumberFormat.getNumberInstance(tr);
        nf0.setMaximumFractionDigits(0);
    }

    private String tl(double v) { return nf2.format(v) + " ₺"; }
    private String tl0(double v) { return nf0.format(Math.round(v)) + " ₺"; }
    private String pct(double v) { return "%" + String.format(new Locale("tr", "TR"), "%.1f", v); }

    private void newPage() {
        if (page != null) finishPage();
        pageNo++;
        page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
        c = page.getCanvas();
        y = M;
        if (pageNo > 1) {
            text("Kafe Ortaklık Masraf Özeti", M, y + 10, 9, false, MUTE, Paint.Align.LEFT);
            text(dateStr, W - M, y + 10, 9, false, MUTE, Paint.Align.RIGHT);
            y += 26;
        }
    }

    private void finishPage() {
        text("Sayfa " + pageNo, W / 2f, H - 22, 9, false, MUTE, Paint.Align.CENTER);
        doc.finishPage(page);
    }

    private void need(float h) { if (y + h > H - M - 10) newPage(); }

    private void text(String s, float x, float yy, float size, boolean bold, int color, Paint.Align al) {
        p.setTextSize(size);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        p.setColor(color);
        p.setTextAlign(al);
        p.setStyle(Paint.Style.FILL);
        c.drawText(s, x, yy, p);
    }

    private String fit(String s, float maxW, float size, boolean bold) {
        p.setTextSize(size);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        if (p.measureText(s) <= maxW) return s;
        int n = p.breakText(s, true, maxW - p.measureText("…"), null);
        return s.substring(0, Math.max(0, n)) + "…";
    }

    private void hline(float yy) {
        p.setColor(LINE);
        p.setStrokeWidth(0.8f);
        c.drawLine(M, yy, W - M, yy, p);
    }

    private void section(String title) {
        need(48);
        y += 14;
        text(title, M, y + 12, 13, true, BROWN, Paint.Align.LEFT);
        y += 22;
    }

    /** cols: x positions; al: alignment per column */
    private void row(String[] v, float[] x, Paint.Align[] al, float[] maxW, boolean bold, int color, float size) {
        need(18);
        for (int i = 0; i < v.length; i++) {
            String s = maxW != null && maxW[i] > 0 ? fit(v[i], maxW[i], size, bold) : v[i];
            text(s, x[i], y + 12, size, bold, color, al[i]);
        }
        y += 17;
        hline(y - 3);
    }

    private void bar(String label, double val, double max, double total, int color) {
        need(18);
        float lw = 150, vx = W - M, bx = M + lw, bw = W - M - lw - 120;
        text(fit(label, lw - 8, 10, false), M, y + 11, 10, false, INK, Paint.Align.LEFT);
        p.setColor(TRACK);
        c.drawRoundRect(new RectF(bx, y + 3, bx + bw, y + 12), 3, 3, p);
        float w = (float) (max > 0 ? val / max * bw : 0);
        if (w > 0) {
            p.setColor(color);
            c.drawRoundRect(new RectF(bx, y + 3, bx + Math.max(w, 2), y + 12), 3, 3, p);
        }
        text(tl0(val) + "  " + pct(total > 0 ? val / total * 100 : 0), vx, y + 11, 10, false, INK, Paint.Align.RIGHT);
        y += 17;
    }

    void write(JSONObject r, File out) throws Exception {
        dateStr = r.optString("date");
        newPage();
        // başlık bandı
        p.setColor(BROWN);
        c.drawRoundRect(new RectF(M, y, W - M, y + 58), 8, 8, p);
        text("Kafe Ortaklık Masraf Özeti", M + 16, y + 26, 18, true, Color.WHITE, Paint.Align.LEFT);
        text("Rapor tarihi: " + dateStr, M + 16, y + 45, 10, false, Color.rgb(0xF3, 0xE6, 0xDA), Paint.Align.LEFT);
        double total = r.optDouble("total"), avg = r.optDouble("avg");
        text(tl(total), W - M - 16, y + 30, 16, true, Color.WHITE, Paint.Align.RIGHT);
        text("toplam masraf", W - M - 16, y + 45, 10, false, Color.rgb(0xF3, 0xE6, 0xDA), Paint.Align.RIGHT);
        y += 70;

        // ortaklar
        section("Ortakların masrafı");
        float[] px = {M, 300, 380, W - M};
        Paint.Align[] pa = {Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT, Paint.Align.RIGHT};
        row(new String[]{"Ortak", "Toplam", "Pay", "Ortalamaya göre"}, px, pa, null, true, MUTE, 9.5f);
        JSONArray ps = r.getJSONArray("partners");
        for (int i = 0; i < ps.length(); i++) {
            JSONObject o = ps.getJSONObject(i);
            double d = o.optDouble("diff");
            String ds = Math.abs(d) < 0.005 ? "Ortalamada" : (d > 0 ? "+" + tl(d) : "−" + tl(-d));
            need(18);
            text(o.optString("n"), px[0], y + 12, 11, true, INK, pa[0]);
            text(tl(o.optDouble("a")), px[1], y + 12, 11, false, INK, pa[1]);
            text(pct(o.optDouble("pct")), px[2], y + 12, 11, false, MUTE, pa[2]);
            text(ds, px[3], y + 12, 11, false, d > 0.005 ? GREEN : d < -0.005 ? RED : MUTE, pa[3]);
            y += 18;
            hline(y - 3);
        }
        need(20);
        text("Kişi başı (¼): " + tl(avg), M, y + 13, 11, true, INK, Paint.Align.LEFT);
        y += 20;

        // türler
        section("Türlere göre");
        JSONArray ks = r.getJSONArray("kinds");
        need(50);
        float x = M, bw = W - 2 * M;
        for (int i = 0; i < ks.length(); i++) {
            double a = ks.getJSONObject(i).optDouble("a");
            if (a <= 0 || total <= 0) continue;
            float w = (float) (a / total * bw);
            p.setColor(KIND[i % 3]);
            c.drawRect(new RectF(x, y, x + Math.max(w - 2, 1), y + 14), p);
            x += w;
        }
        y += 24;
        float lx = M;
        for (int i = 0; i < ks.length(); i++) {
            JSONObject o = ks.getJSONObject(i);
            p.setColor(KIND[i % 3]);
            c.drawRoundRect(new RectF(lx, y + 3, lx + 9, y + 12), 2, 2, p);
            String s = o.optString("n") + ": " + tl0(o.optDouble("a")) + "  " + pct(total > 0 ? o.optDouble("a") / total * 100 : 0);
            text(s, lx + 14, y + 11, 10, false, INK, Paint.Align.LEFT);
            p.setTextSize(10);
            lx += 14 + p.measureText(s) + 22;
        }
        y += 20;

        // kategoriler
        section("Kategorilere göre");
        JSONArray cs = r.getJSONArray("cats");
        double cmax = cs.length() > 0 ? cs.getJSONObject(0).optDouble("a") : 0;
        for (int i = 0; i < cs.length(); i++) {
            JSONObject o = cs.getJSONObject(i);
            bar(o.optString("n"), o.optDouble("a"), cmax, total, BLUE);
        }

        // aylar
        section("Aylara göre");
        JSONArray ms = r.getJSONArray("months");
        double mmax = 0;
        for (int i = 0; i < ms.length(); i++) mmax = Math.max(mmax, ms.getJSONObject(i).optDouble("a"));
        for (int i = 0; i < ms.length(); i++) {
            JSONObject o = ms.getJSONObject(i);
            bar(o.optString("n"), o.optDouble("a"), mmax, total, BLUE);
        }
        double nod = r.optDouble("nodate", 0);
        if (nod > 0) {
            need(16);
            text("Tarihsiz kayıtlar: " + tl0(nod), M, y + 11, 9.5f, false, MUTE, Paint.Align.LEFT);
            y += 16;
        }

        // taşeronlar
        JSONArray ss = r.getJSONArray("subs");
        if (ss.length() > 0) {
            section("Taşeronlar");
            float[] sx = {M, 250, 340, 430, W - M};
            Paint.Align[] sa = {Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.RIGHT, Paint.Align.RIGHT};
            float[] sw = {200, 80, 0, 0, 0};
            row(new String[]{"Taşeron", "İş", "Anlaşma", "Ödenen", "Kalan"}, sx, sa, null, true, MUTE, 9.5f);
            double tc = 0, tp = 0;
            for (int i = 0; i < ss.length(); i++) {
                JSONObject o = ss.getJSONObject(i);
                double cc = o.optDouble("c"), pd = o.optDouble("p");
                tc += cc; tp += pd;
                String kal = cc > 0 ? (cc - pd < -0.005 ? "Fazla " + tl0(pd - cc) : cc - pd > 0.005 ? tl0(cc - pd) : "Bitti") : "—";
                row(new String[]{o.optString("n"), o.optString("j"), cc > 0 ? tl0(cc) : "—", tl0(pd), kal}, sx, sa, sw, false, INK, 10);
            }
            row(new String[]{"Toplam", "", tl0(tc), tl0(tp), tl0(Math.max(tc - tp, 0))}, sx, sa, sw, true, INK, 10);
        }

        // masraf listesi
        JSONArray it = r.getJSONArray("items");
        newPage();
        section("Masraf listesi (" + it.length() + " kayıt)");
        float[] ix = {M, 98, 158, 212, 300, W - M};
        Paint.Align[] ia = {Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.LEFT, Paint.Align.RIGHT};
        float[] iw = {56, 58, 52, 86, 170, 0};
        String[] head = {"Tarih", "Kim", "Tür", "Kategori", "Açıklama", "Tutar"};
        row(head, ix, ia, null, true, MUTE, 9);
        for (int i = 0; i < it.length(); i++) {
            if (y + 18 > H - M - 10) { newPage(); row(head, ix, ia, null, true, MUTE, 9); }
            JSONObject o = it.getJSONObject(i);
            row(new String[]{o.optString("t"), o.optString("p"), o.optString("k"), o.optString("c"), o.optString("d"), tl(o.optDouble("a"))}, ix, ia, iw, false, INK, 9);
        }
        row(new String[]{"", "", "", "", "Toplam", tl(total)}, ix, ia, iw, true, INK, 9.5f);

        finishPage();
        page = null;
        try (FileOutputStream fo = new FileOutputStream(out)) { doc.writeTo(fo); }
        doc.close();
    }
}
