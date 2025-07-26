package editor;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.io.File;

public class FileTreeRenderer extends DefaultTreeCellRenderer {
    private final Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
    private final Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
    private Icon cFileIcon;
    private Icon hFileIcon;

    public FileTreeRenderer() {
        try {
            cFileIcon = new ImageIcon(getClass().getResource("/icons/c_file.png"));
            hFileIcon = new ImageIcon(getClass().getResource("/icons/h_file.png"));
        } catch (Exception e) {
            cFileIcon = fileIcon;
            hFileIcon = fileIcon;
        }
    }

    
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        File file = (File) node.getUserObject();
        
        //显示文件名
        setText(file.getName());
        
        if (file.isDirectory()) {
            setIcon(folderIcon);
        } else {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".c")) {
                setIcon(cFileIcon);
            } else if (name.endsWith(".h")) {
                setIcon(hFileIcon);
            } else {
                setIcon(fileIcon);
            }
        }
        
        return this;
    }
}