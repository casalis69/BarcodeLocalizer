/*
 * Copyright (C) 2014 karthik
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package karthik;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.highgui.Highgui;

/**
 *
 * @author karthik
 */
public class ImageDisplay extends JPanel {

    BufferedImage image;
    private static JPanel imagePanel;
    private static JFrame frame;

    private ImageDisplay(String image_file) {
        try {
            /**
             * ImageIO.read() returns a BufferedImage object, decoding the
             * supplied file with an ImageReader, chosen automatically from
             * registered files The File is wrapped in an ImageInputStream
             * object, so we don't need one. Null is returned, If no registered
             * ImageReader claims to be able to read the resulting stream.
             */
            image = ImageIO.read(new File(image_file));
        } catch (IOException e) {
            //Let us know what happened  
            System.out.println("Error reading dir: " + e.getMessage());
        }

    }

    private ImageDisplay(Mat openCV_img) {
        try {
            image = getBufImg(openCV_img);
        } catch (IOException e) {
            //Let us know what happened  
            System.out.println("Error converting openCV image: " + e.getMessage());
        }
    }

    private ImageDisplay(BufferedImage img){
        image = img;
    }
    
    protected static BufferedImage getBufImg(Mat image) throws IOException {
        // converts image in an openCV Mat object into a Java BufferedImage 
        MatOfByte bytemat = new MatOfByte();
        Highgui.imencode(".jpg", image, bytemat);
        InputStream in = new ByteArrayInputStream(bytemat.toArray());
        BufferedImage img = ImageIO.read(in);
        return img;
    }

    public Dimension getPreferredSize() {
        //We set our preferred size if we succeeded in loading image  
        if (image == null)
            return new Dimension(100, 100);
        else
            return new Dimension(image.getWidth(null), image.getHeight(null));
    }

    public void paint(Graphics g) {
        //Draw our image on the screen with Graphic's "drawImage()" method  
        g.drawImage(image, 0, 0, null);
    }

    public static void showImageFrame(String image_file) {

        frame = new JFrame(image_file);
        imagePanel = new JPanel();
        imagePanel.add(new ImageDisplay(image_file));
        frame.add(imagePanel);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public static void showImageFrame(Mat openCV_img, String title) {

        frame = new JFrame(title);
        imagePanel = new JPanel();
        imagePanel.add(new ImageDisplay(openCV_img));
        frame.add(imagePanel);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public static void showImageFrame(BufferedImage img, String title) {

        frame = new JFrame(title);
        imagePanel = new JPanel();
        imagePanel.add(new ImageDisplay(img));
        frame.add(imagePanel);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
}
