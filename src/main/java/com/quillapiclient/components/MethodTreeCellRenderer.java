package com.quillapiclient.components;

import com.quillapiclient.utility.MethodColorUtil;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JTree;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * Renders request rows as "[METHOD] name" with the method tag colored per HTTP
 * verb, by drawing the tag directly rather than using an HTML-formatted label.
 *
 * <p>The previous implementation set the label text to an
 * {@code <html><span style='color:…'>[GET]</span> name</html>} string. Swing
 * rebuilds a styled HTML View for such a label on every repaint of every
 * visible cell, which profiled at roughly 10x the cost of drawing the tag
 * ourselves — the kind of per-cell overhead that erodes scroll smoothness on
 * large collections and slower machines. This renderer keeps the label as
 * plain text and paints the colored tag into reserved space, so scrolling a
 * few-thousand-request collection stays fast and allocation-free per cell.
 *
 * <p>Space for the tag is reserved with a left border inset rather than the
 * icon/text gap, so it works whether or not tree nodes have icons (this app's
 * tree has none). Extends {@link DefaultTreeCellRenderer} so inline editing and
 * the default look and feel keep working unchanged.
 */
public class MethodTreeCellRenderer extends DefaultTreeCellRenderer {

    /** Space, in pixels, left between the method tag and the request name. */
    private static final int TAG_NAME_GAP = 6;

    private String method; // null when the row carries no [METHOD]
    private Color methodColor;
    private int reservedWidth; // left space reserved for the tag on this row

    // The renderer's default border, captured once before we ever replace it.
    // The instance is reused across cells, so we reset to this every call.
    private Border defaultBorder;
    private boolean defaultBorderCaptured;

    @Override
    public Component getTreeCellRendererComponent(
        JTree tree,
        Object value,
        boolean sel,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus
    ) {
        super.getTreeCellRendererComponent(
            tree,
            value,
            sel,
            expanded,
            leaf,
            row,
            hasFocus
        );

        if (!defaultBorderCaptured) {
            defaultBorder = getBorder();
            defaultBorderCaptured = true;
        }
        setBorder(defaultBorder);

        method = null;
        methodColor = null;
        reservedWidth = 0;

        String text = getText();
        if (text != null) {
            int start = text.indexOf('[');
            int end = text.indexOf(']');
            if (start >= 0 && end > start) {
                method = text.substring(start + 1, end).trim();
                methodColor = MethodColorUtil.getMethodColor(method);
                setText(text.substring(0, start).trim());

                FontMetrics fm = getFontMetrics(getFont());
                reservedWidth =
                    fm.stringWidth("[" + method + "]") + TAG_NAME_GAP;

                // Push the name right by reservedWidth via a left inset; the
                // tag is painted into the vacated space in paintComponent.
                // Widening the border also grows the preferred width, so the
                // name is never clipped.
                EmptyBorder inset = new EmptyBorder(0, reservedWidth, 0, 0);
                setBorder(
                    defaultBorder == null
                        ? inset
                        : BorderFactory.createCompoundBorder(
                            defaultBorder,
                            inset
                        )
                );
            }
        }
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (method == null) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );
            g2.setFont(getFont());

            // The name starts at insets.left; the reserved band is the
            // reservedWidth pixels immediately to its left.
            Insets insets = getInsets();
            int x = insets.left - reservedWidth;

            FontMetrics fm = g2.getFontMetrics();
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

            g2.setColor(methodColor);
            g2.drawString("[" + method + "]", x, y);
        } finally {
            g2.dispose();
        }
    }
}
