package editor;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.tree.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

import javax.swing.border.*;
import org.json.*;

public class CEditor extends JFrame {
    private JTextPane codeArea;
    private JTree fileTree;
    private DefaultTreeModel treeModel;
    private JTextArea debugOutput;
    private JTextArea terminal;
    private File currentFile;
    private JTabbedPane tabbedPane;
    private Map<String, JPanel> openFiles = new HashMap<>();
    private JCheckBoxMenuItem autoSaveItem;
    private boolean autoSaveEnabled = false;
    private JTabbedPane bottomTabbedPane;
    public CEditor() {
        setTitle("EASYC IDE ver-0.1.4");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveSettings();
                dispose();
            }
        });
        
        setLayout(new BorderLayout());
        createMenuBar();
        JSplitPane mainSplitPane = createMainContent();
        //add(mainSplitPane, BorderLayout.CENTER);
        JPanel bottomPanel = createBottomPanel();
        //add(bottomPanel, BorderLayout.SOUTH);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainSplitPane, bottomPanel);
        splitPane.setResizeWeight(0.8); 
        splitPane.setOneTouchExpandable(true); 
        getContentPane().add(splitPane);
        
        loadSettings();
        initWorkspace();
        try {
            Image icon = ImageIO.read(new File("easyc.png"));
            setIconImage(icon);
        } catch (IOException e) {
            e.printStackTrace();
        }
        setVisible(true);
    }
    
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        //菜单
        JMenu fileMenu = new JMenu("文件");
        JMenuItem newItem = new JMenuItem("新建");
        JMenuItem openItem = new JMenuItem("打开");
        JMenuItem saveItem = new JMenuItem("保存");
        JMenuItem setRootItem = new JMenuItem("设置根目录");
        autoSaveItem = new JCheckBoxMenuItem("自动保存");
        JMenuItem exitItem = new JMenuItem("退出");
        
        newItem.addActionListener(e -> newFile());
        openItem.addActionListener(e -> openFile());
        saveItem.addActionListener(e -> saveFile());
        setRootItem.addActionListener(e -> setRootDirectory());
        autoSaveItem.addActionListener(e -> {
            autoSaveEnabled = autoSaveItem.isSelected();
            if (autoSaveEnabled && currentFile != null) {
                saveFile();
            }
        });
        exitItem.addActionListener(e -> {
            saveSettings();
            dispose();
        });
        
        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.addSeparator();
        fileMenu.add(setRootItem);
        fileMenu.addSeparator();
        fileMenu.add(autoSaveItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        // 编译
        JMenu buildMenu = new JMenu("编译");
        JMenuItem compileItem = new JMenuItem("编译");
        JMenuItem debugItem = new JMenuItem("调试");
        JMenuItem settingsItem = new JMenuItem("编译设置");

        compileItem.addActionListener(e -> compileCode());
        debugItem.addActionListener(e -> debugCode());
        settingsItem.addActionListener(e -> showCompileSettings());

        buildMenu.add(compileItem);
        buildMenu.add(debugItem);
        buildMenu.add(settingsItem);
        
        menuBar.add(fileMenu);
        //menuBar.add(editMenu);
        menuBar.add(buildMenu);
        
        setJMenuBar(menuBar);
    }
    
    private JSplitPane createMainContent() {
    //文件树
        fileTree = new JTree();
        fileTree.setCellRenderer(new FileTreeRenderer());
        fileTree.setPreferredSize(new Dimension(200, getHeight()));
        fileTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
            if (node == null || !node.isLeaf()) return;
            
            Object nodeInfo = node.getUserObject();
            if (nodeInfo instanceof File) {
                File selectedFile = (File) nodeInfo;
                if (selectedFile.isFile()) {
                    JPanel existingPanel = openFiles.get(selectedFile.getAbsolutePath());
                    if (existingPanel != null) {
                        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                            if (tabbedPane.getComponentAt(i) == existingPanel) {
                                tabbedPane.setSelectedIndex(i);
                                return;
                            }
                        }
                        openFiles.remove(selectedFile.getAbsolutePath());
                    }
                    openFileInEditor(selectedFile);
                }
            }
        });
        
        JScrollPane treeScrollPane = new JScrollPane(fileTree);
        
        //右侧编辑区
        tabbedPane = new JTabbedPane();
        tabbedPane.addChangeListener(e -> {
            Component selected = tabbedPane.getSelectedComponent();
            if (selected instanceof JPanel) {
                JScrollPane scrollPane = (JScrollPane)((JPanel)selected).getComponent(0);
                JViewport viewport = scrollPane.getViewport();
                JTextPane textPane = (JTextPane)viewport.getView();
                currentFile = findFileForEditor(textPane);
            }
        });
        
        codeArea = createCodeArea();
        //addNewTab("未命名", codeArea, null);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, 
                                            treeScrollPane, tabbedPane);
        splitPane.setDividerLocation(200);
        
        return splitPane;

    }
    
    private JPanel createBottomPanel() {
        bottomTabbedPane = new JTabbedPane();
        //output
        debugOutput = new JTextArea();
        debugOutput.setEditable(false);
        JScrollPane debugScrollPane = new JScrollPane(debugOutput);
        bottomTabbedPane.addTab("调试输出", debugScrollPane);
        //terminal
        addTerminalTab();
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(bottomTabbedPane, BorderLayout.CENTER);
        return panel;
    }
    
    private JTextPane createCodeArea() {
        JTextPane textPane = new JTextPane();
        textPane.setFont(new Font("Consolas", Font.PLAIN, 14));
        
        //highlight
        StyledDocument doc = textPane.getStyledDocument();
        StyleContext context = new StyleContext();
        Style defaultStyle = context.getStyle(StyleContext.DEFAULT_STYLE);
        Style cwStyle = context.addStyle("ConstantWidth", defaultStyle);
        StyleConstants.setFontFamily(cwStyle, "Consolas");
        
        // 自动保存监听
        doc.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                if (autoSaveEnabled && currentFile != null) {
                    saveFile();
                }
            }
            public void removeUpdate(DocumentEvent e) {
                if (autoSaveEnabled && currentFile != null) {
                    saveFile();
                }
            }
            public void changedUpdate(DocumentEvent e) {}
        });
        
        return textPane;
    }
    
    private void initFileTree(String rootPath) {
        File rootDir = new File(rootPath);
        if (!rootDir.exists()) {
            rootDir = new File(System.getProperty("user.home"));
        }
        
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(rootDir);
        buildTree(rootNode, rootDir);
        
        treeModel = new DefaultTreeModel(rootNode);
        fileTree.setModel(treeModel);

        fileTree.setCellRenderer(new FileTreeRenderer());
        // 展开根节点
        fileTree.expandPath(new TreePath(rootNode.getPath()));
    }
    private void buildTree(DefaultMutableTreeNode node, File file) {
            if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) {
                        return -1;
                    } else if (!f1.isDirectory() && f2.isDirectory()) {
                        return 1;
                    } else {
                        return f1.getName().compareToIgnoreCase(f2.getName());
                    }
                });
                
                for (File child : files) {
                    DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
                    node.add(childNode);
                    buildTree(childNode, child);
                }
            }
        }
    }
    
    private void newFile() {
        codeArea = createCodeArea();
        addNewTab("未命名", codeArea, null);
        currentFile = null;
    }
    
    private void openFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        int result = fileChooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            openFileInEditor(selectedFile);
        }
    }
    
    private void openFileInEditor(File file) {
        try {
            // 再次检查文件是否已打开
            JPanel existingPanel = openFiles.get(file.getAbsolutePath());
            if (existingPanel != null) {
                for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                    if (tabbedPane.getComponentAt(i) == existingPanel) {
                        tabbedPane.setSelectedIndex(i);
                        return;
                    }
                }
                // 如果面板不存在，从openFiles中移除
                openFiles.remove(file.getAbsolutePath());
            }
            
            // 读取文件内容
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder content = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            
            // 创建新编辑器
            JTextPane newCodeArea = createCodeArea();
            newCodeArea.setText(content.toString());
            
            // 添加新标签页
            addNewTab(file.getName(), newCodeArea, file);
            
            currentFile = file;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "无法打开文件: " + e.getMessage(), 
                                        "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void addNewTab(String title, JTextPane textPane, File file) {
        JPanel panel = new JPanel(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(textPane);
        
        TextLineNumber tln = new TextLineNumber(textPane);
        scrollPane.setRowHeaderView(tln);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabPanel.setOpaque(false);
        JLabel label = new JLabel(title);
        JButton closeButton = new JButton("×");
        closeButton.setBorder(BorderFactory.createEmptyBorder());
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusable(false);
        closeButton.setMargin(new Insets(0, 5, 0, 5));
        
        closeButton.addActionListener(e -> {
            int index = tabbedPane.indexOfTabComponent(tabPanel);
            if (index != -1) {
                if (file != null) {
                    openFiles.remove(file.getAbsolutePath());
                }
                tabbedPane.remove(index);
            }
        });
        
        tabPanel.add(label);
        tabPanel.add(closeButton);
        
        tabbedPane.addTab(title, panel);
        tabbedPane.setTabComponentAt(tabbedPane.getTabCount() - 1, tabPanel);
        tabbedPane.setSelectedComponent(panel);

        if (file != null) {
        textPane.putClientProperty("associatedFile", file);
        openFiles.put(file.getAbsolutePath(), panel); // 直接存储panel而不是tabComponent
        }
    }

    private void safelyRemoveTab(int index, File file) {
        if (index < 0 || index >= tabbedPane.getTabCount()) {
            return;
        }
        
        // 获取要移除的面板
        Component tab = tabbedPane.getComponentAt(index);
        
        // 从openFiles中移除
        if (file != null) {
            openFiles.remove(file.getAbsolutePath());
        } else if (tab instanceof JPanel) {
            // 尝试通过组件查找关联文件
            JScrollPane scrollPane = (JScrollPane)((JPanel)tab).getComponent(0);
            JViewport viewport = scrollPane.getViewport();
            if (viewport.getView() instanceof JTextPane) {
                JTextPane textPane = (JTextPane)viewport.getView();
                File associatedFile = (File)textPane.getClientProperty("associatedFile");
                if (associatedFile != null) {
                    openFiles.remove(associatedFile.getAbsolutePath());
                }
            }
        }
        
        // 安全移除标签页
        tabbedPane.remove(index);
        
        // 更新当前文件引用
        if (tabbedPane.getTabCount() > 0) {
            int newIndex = Math.min(index, tabbedPane.getTabCount() - 1);
            Component selected = tabbedPane.getComponentAt(newIndex);
            if (selected instanceof JPanel) {
                JScrollPane scrollPane = (JScrollPane)((JPanel)selected).getComponent(0);
                JViewport viewport = scrollPane.getViewport();
                if (viewport.getView() instanceof JTextPane) {
                    currentFile = (File)((JTextPane)viewport.getView()).getClientProperty("associatedFile");
                }
            }
        } else {
            currentFile = null;
        }
    }
    private File findFileForEditor(JTextPane editor) {
        // 检查编辑器是否有关联文件属性
        File associatedFile = (File)editor.getClientProperty("associatedFile");
        if (associatedFile != null) {
            return associatedFile;
        }
        
        // 遍历所有标签页查找匹配的编辑器
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component tab = tabbedPane.getComponentAt(i);
            if (tab instanceof JPanel) {
                JScrollPane scrollPane = (JScrollPane)((JPanel)tab).getComponent(0);
                JViewport viewport = scrollPane.getViewport();
                if (viewport.getView() == editor) {
                    // 尝试从openFiles反向查找
                    for (Map.Entry<String, JPanel> entry : openFiles.entrySet()) {
                        if (entry.getValue() == tab) {
                            return new File(entry.getKey());
                        }
                    }
                }
            }
        }
        return null;
    }
    private void saveFile() {
        if (currentFile == null) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
            int result = fileChooser.showSaveDialog(this);
            
            if (result == JFileChooser.APPROVE_OPTION) {
                currentFile = fileChooser.getSelectedFile();
            } else {
                return;
            }
        }
        
        try {
            Component selected = tabbedPane.getSelectedComponent();
            if (selected instanceof JPanel) {
                JScrollPane scrollPane = (JScrollPane)((JPanel)selected).getComponent(0);
                JViewport viewport = scrollPane.getViewport();
                JTextPane textPane = (JTextPane)viewport.getView();
                
                BufferedWriter writer = new BufferedWriter(new FileWriter(currentFile));
                writer.write(textPane.getText());
                writer.close();
                
                // 更新标签标题
                int selectedIndex = tabbedPane.getSelectedIndex();
                JPanel tabComponent = (JPanel)tabbedPane.getTabComponentAt(selectedIndex);
                JLabel label = (JLabel)tabComponent.getComponent(0);
                label.setText(currentFile.getName());
                
                debugOutput.append("文件保存成功: " + currentFile.getAbsolutePath() + "\n");
                
                // 更新openFiles
                openFiles.put(currentFile.getAbsolutePath(), tabComponent);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "无法保存文件: " + e.getMessage(), 
                                      "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void setRootDirectory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        int result = fileChooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = fileChooser.getSelectedFile();
            initFileTree(selectedDir.getAbsolutePath());
        }
    }
    
    private void compileCode() {
        if (workspaceRoot == null) {
            initWorkspace();
        }
        
        // 确定主文件路径 - 优先使用当前打开的文件
        File mainFile;
        // if (currentFile != null && currentFile.getName().endsWith(".c")) {
        //     mainFile = currentFile;
        //     // 如果设置中的主文件不是当前文件，更新设置
        //     if (!currentSettings.mainFile.equals(
        //             workspaceRoot.toPath().relativize(currentFile.toPath()).toString())) {
        //         currentSettings.mainFile = workspaceRoot.toPath()
        //             .relativize(currentFile.toPath()).toString();
        //         currentSettings.outputPath = workspaceRoot.toPath()
        //             .relativize(currentFile.toPath()).toString()
        //             .replace(".c", ".exe");
        //         saveCompileSettings();
        //     }
        // } else if (currentSettings.mainFile != null && !currentSettings.mainFile.isEmpty()) {
        //     mainFile = new File(workspaceRoot, currentSettings.mainFile);
        // } else {
        //     JOptionPane.showMessageDialog(this, "请先打开或指定C文件", 
        //                                 "错误", JOptionPane.ERROR_MESSAGE);
        //     return;
        // }
        
        if(currentSettings.mainFile.equals("${file}") ) {
            //mainFile=new File(workspaceRoot,workspaceRoot.toPath().relativize(currentFile.toPath()).toString());
            mainFile=currentFile;
            //System.out.println("123");
        }else if (currentSettings.mainFile != null && !currentSettings.mainFile.isEmpty()) {
             mainFile = new File(workspaceRoot, currentSettings.mainFile);
             //System.out.println(currentSettings.mainFile);
             //System.out.println(currentFile.getName());
        }
        else {
             JOptionPane.showMessageDialog(this, "请先打开或指定C文件", 
                                        "错误", JOptionPane.ERROR_MESSAGE);
             return;
        }

        // 确定输出路径 - 优先使用当前文件的同名.exe
        File outputFile;
        if (currentSettings.outputPath=="${filexe}" && currentFile != null && currentFile.getName().endsWith(".c")) {
            String exePath = currentFile.getAbsolutePath().replace(".c", ".exe");
            outputFile = new File(exePath);
        } else {
            outputFile = new File(workspaceRoot, currentSettings.outputPath);
        }
        
        // 确保输出目录存在
        outputFile.getParentFile().mkdirs();
        
        debugOutput.setText("");
        debugOutput.append("开始编译: " + mainFile.getName() + "\n");
        debugOutput.append("输出文件: " + outputFile.getAbsolutePath() + "\n");
        
        new Thread(() -> {
            try {
                // 构建gcc命令
                List<String> compileCmd = new ArrayList<>();
                compileCmd.add("gcc");
                
                // 添加包含路径
                for (String includePath : currentSettings.includePaths) {
                    compileCmd.add("-I");
                    compileCmd.add(new File(workspaceRoot, includePath).getAbsolutePath());
                    System.out.println(new File(workspaceRoot, includePath).getAbsolutePath());
                }
                System.out.println(mainFile.getAbsolutePath());
                // 添加主文件和输出路径
                compileCmd.add(mainFile.getAbsolutePath());
                compileCmd.add("-o");
                compileCmd.add(outputFile.getAbsolutePath());
                
                // 执行编译
                Process process = Runtime.getRuntime().exec(compileCmd.toArray(new String[0]));
                
                // 处理输出
                BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()));
                
                StringBuilder errorOutput = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
                
                int exitCode = process.waitFor();
                
                SwingUtilities.invokeLater(() -> {
                    if (exitCode == 0) {
                        debugOutput.append("编译成功！\n");
                        debugOutput.append("输出文件: " + outputFile.getAbsolutePath() + "\n");
                        runExecutable(outputFile.getAbsolutePath());
                    } else {
                        debugOutput.append("编译失败！错误代码: " + exitCode + "\n");
                        debugOutput.append("错误信息:\n");
                        debugOutput.append(errorOutput.toString());
                    }
                });
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    debugOutput.append("编译过程出错: " + e.getMessage() + "\n");
                });
            }
        }).start();
    }

private void runExecutable(String exePath) {
    new Thread(() -> {
        try {
            String[] cmd = {
                "cmd.exe",
                "/c", 
                "start", 
                "cmd", 
                "/k", 
                "\"" + exePath + "\""
            };
            
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
            
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                debugOutput.append("执行失败: " + e.getMessage() + "\n");
            });
        }
    }).start();
}
    private void debugCode() {
        if (currentFile == null) {
            JOptionPane.showMessageDialog(this, "请先保存文件", 
                                        "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 清空调试输出
        debugOutput.setText("");
        debugOutput.append("开始编译: " + currentFile.getName() + "\n");
        
        // 获取文件所在目录和基本名称
        String sourcePath = currentFile.getAbsolutePath();
        String dir = currentFile.getParent();
        String baseName = sourcePath.substring(0, sourcePath.lastIndexOf('.'));
        String exePath = baseName + ".exe";
        
        new Thread(() -> {
            try {
                //构建gcc命令
                String[] compileCmd = {"gdb", exePath};
                //执行编译命令
                Process process = Runtime.getRuntime().exec(compileCmd);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                
                while ((line = reader.readLine()) != null) {
                    String finalLine = line;
                    SwingUtilities.invokeLater(() -> debugOutput.append(finalLine + "\n"));
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    debugOutput.append("调试错误: " + e.getMessage() + "\n");
                });
            }
        }).start();
    }
    private void printPrompt(BufferedWriter writer) {
        try {
            writer.write("prompt");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("无法获取提示符");
        }
    }
    private void addTerminalTab() {
        JPanel terminalPanel = new JPanel(new BorderLayout());
        JTextArea outputArea = new JTextArea();
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);

        JTextField inputField = new JTextField();
        terminalPanel.add(scrollPane, BorderLayout.CENTER);
        terminalPanel.add(inputField, BorderLayout.SOUTH);

        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoExit", "-Command", "chcp 65001");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            new Thread(() -> {
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        outputArea.append(line + "\n");
                        outputArea.setCaretPosition(outputArea.getDocument().getLength());
                    }
                } catch (IOException ex) {
                    outputArea.append("[终端输出错误]\n");
                }
            }).start();

            inputField.addActionListener(e -> {
                String cmd = inputField.getText();
                try {
                    writer.write(cmd);
                    writer.newLine();
                    writer.flush();
                    inputField.setText("");
                } catch (IOException ex) {
                    outputArea.append("[命令发送失败]\n");
                }
            });

            //插入tab
            int insertIndex = Math.max(0,bottomTabbedPane.getTabCount() - 1);
            bottomTabbedPane.insertTab("终端"/* + (insertIndex + 1)*/ , null, terminalPanel, null, insertIndex);
            bottomTabbedPane.setSelectedIndex(insertIndex);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "启动 PowerShell 失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void loadSettings() {
        JSONObject settings = SettingsManager.loadSettings();
        if (settings.has("rootDirectory")) {
            initFileTree(settings.getString("rootDirectory"));
        }
        if (settings.has("openFiles")) {
            JSONArray files = settings.getJSONArray("openFiles");
            for (int i = 0; i < files.length(); i++) {
                File file = new File(files.getString(i));
                if (file.exists()) {
                    openFileInEditor(file);
                }
            }
        }
        if (settings.has("autoSave")) {
            autoSaveEnabled = settings.getBoolean("autoSave");
            autoSaveItem.setSelected(autoSaveEnabled);
        }
    }
    
    private void saveSettings() {
        List<String> openFilePaths = new ArrayList<>();
        for (String path : openFiles.keySet()) {
            openFilePaths.add(path);
        }
        
        File rootDir = (File)((DefaultMutableTreeNode)treeModel.getRoot()).getUserObject();
        SettingsManager.saveSettings(rootDir.getAbsolutePath(), openFilePaths, autoSaveEnabled);
    }
    // 编译设置
    private static class CompileSettings {
        public List<String> includePaths = new ArrayList<>();
        public String mainFile;
        public String outputPath;
        
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("includePaths", new JSONArray(includePaths));
            json.put("mainFile", mainFile);
            json.put("outputPath", outputPath);
            return json;
        }
        
        public static CompileSettings fromJson(JSONObject json) {
            CompileSettings settings = new CompileSettings();
            if (json.has("includePaths")) {
                JSONArray includes = json.getJSONArray("includePaths");
                for (int i = 0; i < includes.length(); i++) {
                    settings.includePaths.add(includes.getString(i));
                }
            }
            if (json.has("mainFile")) {
                settings.mainFile = json.getString("mainFile");
            }
            if (json.has("outputPath")) {
                settings.outputPath = json.getString("outputPath");
            }
            return settings;
        }
    }

    // 当前编译设置
    private CompileSettings currentSettings = new CompileSettings();
    private File workspaceRoot;

    // 初始化工作区
    private void initWorkspace() {
        // 获取工作区根目录
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)treeModel.getRoot();
        workspaceRoot = (File)rootNode.getUserObject();
        
        // 创建.easyc目录（如果不存在）
        File easycDir = new File(workspaceRoot, ".easyc");
        if (!easycDir.exists()) {
            easycDir.mkdir();
        }
        
        // 加载或创建默认编译设置
        loadCompileSettings();
    }

    private void loadCompileSettings() {
        File settingsFile = new File(workspaceRoot, ".easyc/compile.json");
        if (settingsFile.exists()) {
            try {
                String content = new String(Files.readAllBytes(settingsFile.toPath()));
                JSONObject json = new JSONObject(content);
                currentSettings = CompileSettings.fromJson(json);
            } catch (IOException e) {
                debugOutput.append("加载编译设置失败: " + e.getMessage() + "\n");
            }
        } else {
            // 设置新的默认值
            currentSettings.includePaths = new ArrayList<>(); // include路径默认为空
            
            // 主文件默认为当前打开的文件（如果有）
            // if (currentFile != null && currentFile.getName().endsWith(".c")) {
            //     currentSettings.mainFile = workspaceRoot.toPath()
            //         .relativize(currentFile.toPath()).toString();
            //     // 输出文件默认为同目录下的同名.exe文件
            //     String exeName = currentFile.getName().replace(".c", ".exe");
            //     currentSettings.outputPath = workspaceRoot.toPath()
            //         .relativize(currentFile.getParentFile().toPath())
            //         .resolve(exeName).toString();
            // } else {
            //     // 如果没有打开.c文件，使用更简单的默认值
            //     // currentSettings.mainFile = "main.c";
            //     // currentSettings.outputPath = "main.exe";
            // }
            currentSettings.mainFile="${file}";
            currentSettings.outputPath="${filexe}";
            saveCompileSettings();
        }
    }

    // 保存编译设置
    private void saveCompileSettings() {
            File settingsFile = new File(workspaceRoot, ".easyc/compile.json");
            try (FileWriter writer = new FileWriter(settingsFile)) {
                writer.write(currentSettings.toJson().toString(2));
            } catch (IOException e) {
                debugOutput.append("保存编译设置失败: " + e.getMessage() + "\n");
            }
        }
        private void showCompileSettings() {
        if (workspaceRoot == null) {
            initWorkspace();
        }
        
        File settingsFile = new File(workspaceRoot, ".easyc/compile.json");
        
        // 如果文件不存在，创建默认设置
        if (!settingsFile.exists()) {
            // 设置新的默认值
            currentSettings = new CompileSettings();
            
            currentSettings.includePaths = new ArrayList<>(); // include路径默认为空
            currentSettings.mainFile="${file}";
            currentSettings.outputPath="${filexe}";
            saveCompileSettings();
    }
    
    // 在编辑区打开compile.json文件
    openFileInEditor(settingsFile);
}
/**
 * 获取当前活动编辑器的文件（如果有）
 */
private File getCurrentCodeFile() {
    if (currentFile != null && currentFile.getName().endsWith(".c")) {
        return currentFile;
    }
    
    Component selected = tabbedPane.getSelectedComponent();
    if (selected instanceof JPanel) {
        JScrollPane scrollPane = (JScrollPane)((JPanel)selected).getComponent(0);
        JViewport viewport = scrollPane.getViewport();
        if (viewport.getView() instanceof JTextPane) {
            JTextPane textPane = (JTextPane)viewport.getView();
            File file = (File)textPane.getClientProperty("associatedFile");
            if (file != null && file.getName().endsWith(".c")) {
                return file;
            }
        }
    }
    return null;
}    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                //UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
                new CEditor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
