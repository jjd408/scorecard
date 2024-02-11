/*
 * The MIT License
 *
 * Copyright 2024 joe.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.joe.scorecard;

import com.joe.scorecard.data.ScData;
import com.joe.scorecard.view.ScFrame;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 *
 * @author joe
 */
public class Scorecard {
    static private ScData data = new ScData();
    static private ScFrame frame = new ScFrame();
    
    static public void open() {
        data.openGlobalData(System.getProperty("user.home") + "/sc.db");
    }

    static public void run() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    frame.setVisible(true);
                }
        });
        } catch (Exception ex) {
            Logger.getLogger(Scorecard.class.getName()).log(Level.SEVERE, null, ex);     
        }
    }
    
    static public void close() {
        data.closeGlobalData();
        System.exit(0);
    }
    
    static public void main(String[] args) {
        Scorecard.open();
        Scorecard.run();
    }

    public static ScData getData() {
        return data;
    }

    public static ScFrame getFrame() {
        return frame;
    }
    
    
}
