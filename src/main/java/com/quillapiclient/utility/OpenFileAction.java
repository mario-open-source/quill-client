package com.quillapiclient.utility;

import java.io.File;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

public class OpenFileAction implements ActionListener {
    private File selectedFile;
    private FileChooserCallback callback;

    public OpenFileAction() {
    }

    public OpenFileAction(FileChooserCallback callback) {
        this.callback = callback;
    }

    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        
        // Filter for JSON files
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
            "Postman Collection Files (*.json)", "json");
        chooser.setFileFilter(filter);
        
        int returnValue = chooser.showOpenDialog(null);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            try {
                selectedFile = chooser.getSelectedFile();
                if (callback != null) {
                    callback.onFileSelected(selectedFile);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public File getSelectedFile() {
        return selectedFile;
    }

    @FunctionalInterface
    public interface FileChooserCallback {
        void onFileSelected(File file);
    }
}
