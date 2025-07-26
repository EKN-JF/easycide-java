package editor;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;

public class TextLineNumber extends JComponent {
    private final JTextPane textPane;
    private final FontMetrics fontMetrics;
    private final int topInset;
    private final int fontAscent;
    private final int fontHeight;
    private final int fontDescent;
    private final int fontLeading;
    
    public TextLineNumber(JTextPane textPane) {
        this.textPane = textPane;
        
        Font font = textPane.getFont();
        fontMetrics = getFontMetrics(font);
        fontHeight = fontMetrics.getHeight();
        fontAscent = fontMetrics.getAscent();
        fontDescent = fontMetrics.getDescent();
        fontLeading = fontMetrics.getLeading();
        
        Border border = textPane.getBorder();
        if (border != null) {
            Insets insets = border.getBorderInsets(textPane);
            topInset = insets.top;
        } else {
            topInset = 0;
        }
        
        setPreferredSize(calculateWidth());
        
        textPane.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void changedUpdate(DocumentEvent e) { update(); }
            
            private void update() {
                SwingUtilities.invokeLater(() -> {
                    setPreferredSize(calculateWidth());
                    revalidate();
                    repaint();
                });
            }
        });
    }
    
    private Dimension calculateWidth() {
        Document doc = textPane.getDocument();
        Element root = doc.getDefaultRootElement();
        int lines = root.getElementCount();
        int digits = Math.max(3, String.valueOf(lines).length());
        
        int width = fontMetrics.charWidth('0') * digits + 10;
        return new Dimension(width, textPane.getHeight());
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        
        g.setColor(getForeground());
        g.setFont(textPane.getFont());
        
        Document doc = textPane.getDocument();
        Element root = doc.getDefaultRootElement();
        
        int lineHeight = fontHeight;
        int baseline = topInset + fontAscent;
        
        Rectangle clip = g.getClipBounds();
        int startLine = Math.max(1, (clip.y - baseline) / lineHeight);
        int endLine = Math.min(root.getElementCount(), 
                            (clip.y + clip.height - baseline) / lineHeight + 1);
        
        for (int i = startLine; i <= endLine; i++) {
            String lineNumber = String.valueOf(i);
            int x = getWidth() - fontMetrics.stringWidth(lineNumber) - 5;
            int y = baseline + (i - 1) * lineHeight;
            g.drawString(lineNumber, x, y);
        }
    }
}