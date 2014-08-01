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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 *
 * @author karthik
 */
public class MatrixBarcode extends Barcode {

    /**
     * @param args the command line arguments
     */
    private double THRESHOLD_T2; // min number of gradient edges in rectangular window to consider as non-zero
    private double THRESHOLD_MIN_AREA; // min area for candidate region to be considered as a barcode

    private static final double THRESHOLD_T3 = 0.6;  // threshold for ratio of contour area to bounding rectangle area
    
    private static final int MAX_ROWS = 300;  //image with more rows than MAX_ROWS is scaled down to make finding barcode easier
    
    private Size elem_size, large_elem_size;

    public MatrixBarcode(String filename) {
        super(filename);
        searchType = CodeType.MATRIX;
        elem_size = new Size(10, 10);
        large_elem_size = new Size(12, 12);

    }

    public MatrixBarcode(String filename, boolean debug) {
        this(filename);
        DEBUG_IMAGES = debug;
    }

    private void preprocess_image() {
        // pre-process image to convert to grayscale and do morph black hat
        // it also reduces image size for large images which helps with processing speed
        // and reducing sensitivity to barcode size within the image
        // shrink image if it is above a certain size
                
        if(rows > MAX_ROWS){
            cols = (int) (cols * (MAX_ROWS * 1.0/rows));
            rows = MAX_ROWS;
        img_details.src_scaled = new Mat(rows, cols, CvType.CV_32F);
        Imgproc.resize(img_details.src_original, img_details.src_scaled, img_details.src_scaled.size(), 0, 0, Imgproc.INTER_AREA);                

        }
        THRESHOLD_MIN_AREA = 0.02 * cols * rows;
        RECT_HEIGHT = (int) (0.1 * rows);
        RECT_WIDTH = (int) (0.1 * cols);
        THRESHOLD_T2 = RECT_HEIGHT * RECT_WIDTH * 0.3;
                
        // do pre-processing to increase contrast
        img_details.src_grayscale = new Mat(rows, cols, CvType.CV_32F);
        Imgproc.cvtColor(img_details.src_scaled, img_details.src_grayscale, Imgproc.COLOR_RGB2GRAY);
        Imgproc.morphologyEx(img_details.src_grayscale, img_details.src_grayscale, Imgproc.MORPH_BLACKHAT,
            Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, elem_size));
        // do some sharpening to remove anti-aliasing effects for 2D barcodes
   /*     Mat kernel = Mat.zeros(3, 3, CvType.CV_32F);
        kernel.put(1, 1, 5);
        kernel.put(0, 1, -1);
        kernel.put(2, 1, -1);
        kernel.put(1, 0, -1);
        kernel.put(1, 2, -1);
        
        Imgproc.filter2D(img_details.src_grayscale, img_details.src_grayscale, -1, kernel);
    //    Core.normalize(img_details.src_grayscale, img_details.src_grayscale, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        //            img_details.src_grayscale.convertTo(img_details.src_grayscale, CvType.CV_8U);
   //     System.out.println("threshold for " + name + " is " + Imgproc.threshold(img_details.src_grayscale, img_details.src_grayscale, 225, 255, Imgproc.THRESH_BINARY));
        
        */
        write_Mat("greyscale.csv", img_details.src_grayscale);
        if (DEBUG_IMAGES) {
            ImageDisplay.showImageFrame(img_details.src_grayscale, "Pre-processed image");
        }
    }

    public List<BufferedImage> findBarcode(){

        System.out.println("Searching " + name + " for " + searchType.name());
        preprocess_image();

        findCandidates();   // find areas with low variance in gradient direction

        // connect large components by doing morph close followed by morph open
        // use larger element size for erosion to remove small elements joined by dilation
        Imgproc.dilate(img_details.E3, img_details.E3, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, elem_size));
        Imgproc.erode(img_details.E3, img_details.E3, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, large_elem_size));

        Imgproc.erode(img_details.E3, img_details.E3, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, elem_size));
        Imgproc.dilate(img_details.E3, img_details.E3, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, large_elem_size));

        if (DEBUG_IMAGES)
            ImageDisplay.showImageFrame(img_details.E3, "Image img_details.E3 after morph close and open");

        List<MatOfPoint> contours = new ArrayList<>();
        // findContours modifies source image so we pass it a copy of img_details.E3
        // img_details.E3 will be used again shortly to expand the barcode region
        Imgproc.findContours(img_details.E3.clone(),
            contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        
        double bounding_rect_area = 0;
        RotatedRect minRect;
        Mat ROI;
        for (int i = 0; i < contours.size(); i++) {
            double area = Imgproc.contourArea(contours.get(i));
            minRect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(i).toArray()));
            bounding_rect_area = minRect.size.width * minRect.size.height;

            if (area < THRESHOLD_MIN_AREA) // ignore contour if it is of too small a region
                continue;

            if ((area / bounding_rect_area) > THRESHOLD_T3) // check if contour is of a rectangular object
            {
                CandidateBarcode cb = new CandidateBarcode(img_details, minRect);
                if(DEBUG_IMAGES)
                    cb.drawCandidateRegion(minRect, new Scalar(0, 255, 0));
                // get candidate regions to be a barcode
                minRect = cb.getCandidateRegion();
                ROI = NormalizeCandidateRegion(minRect);                
                try{
                candidateBarcodes.add(ImageDisplay.getBufImg(ROI));
                }
                catch(IOException ioe){
                    System.out.println("Error when creating image " + ioe.getMessage());
                    return null;
                }
                if (DEBUG_IMAGES) {
                    cb.drawCandidateRegion(minRect, new Scalar(0, 0, 255));
                    ImageDisplay.showImageFrame(ROI, "Cropped image of " + name);
                }
            }
        }
        if(DEBUG_IMAGES)
            ImageDisplay.showImageFrame(img_details.src_scaled, name + " with candidate regions");

        return candidateBarcodes;
    }

 
    private void findCandidates() {
        // find candidate regions that may contain barcodes
        // modifies class variable img_details.E3 to contain image img_details.E3
        // also modifies class variable img_details.gradient_direction to contain gradient directions
        Mat probabilities;
        img_details.gradient_direction = Mat.zeros(rows, cols, CvType.CV_32F);

        double angle;
        Mat scharr_x, scharr_y;
        scharr_x = new Mat(rows, cols, CvType.CV_32F);
        scharr_y = new Mat(rows, cols, CvType.CV_32F);

        Imgproc.Scharr(img_details.src_grayscale, scharr_x, CvType.CV_32F, 1, 0);
        Imgproc.Scharr(img_details.src_grayscale, scharr_y, CvType.CV_32F, 0, 1);

        // calc angle using Core.phase function - should be quicker than using atan2 manually
        Core.phase(scharr_x, scharr_y, img_details.gradient_direction, true);

        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) {
                angle = img_details.gradient_direction.get(i, j)[0];
                angle = angle % 180;
                angle = (angle > 170) ? 0 : angle;
                img_details.gradient_direction.put(i, j, angle);
            }

        // convert type after modifying angle so that angles above 360 don't get truncated
        img_details.gradient_direction.convertTo(img_details.gradient_direction, CvType.CV_8U);
        write_Mat("angles.csv", img_details.gradient_direction);

        // calculate magnitude of gradient
        img_details.gradient_magnitude = Mat.zeros(scharr_x.size(), scharr_x.type());
        Core.magnitude(scharr_x, scharr_y, img_details.gradient_magnitude);
        write_Mat("magnitudes_raw.csv", img_details.gradient_magnitude);
        Core.normalize(img_details.gradient_magnitude, img_details.gradient_magnitude, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        write_Mat("magnitudes_normalized.csv", img_details.gradient_magnitude);
        Imgproc.threshold(img_details.gradient_magnitude, img_details.gradient_magnitude, 50, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        write_Mat("magnitudes.csv", img_details.gradient_magnitude);

        probabilities = calcHistogramProbabilities();
        write_Mat("probabilities_raw.csv", probabilities);
        Core.normalize(probabilities, probabilities, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);        
        System.out.println("Prob threshold is " + Imgproc.threshold(probabilities, probabilities, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU));
        write_Mat("probabilities.csv", probabilities);
        if (DEBUG_IMAGES){
            ImageDisplay.showImageFrame(img_details.gradient_magnitude, "Magnitudes");
            ImageDisplay.showImageFrame(probabilities, "histogram probabilities");            
        }
        img_details.E3 = probabilities;
    }

    private Mat calcHistogramProbabilities() {
        int right_col, left_col, top_row, bottom_row;
        int DUMMY_ANGLE = -1;
        int BIN_WIDTH = 15;

        MatOfInt hist = new MatOfInt();
        Mat imgWindow; // used to hold sub-matrices from the image that represent the window around the current point
        int bins = 180 / BIN_WIDTH;

        MatOfInt mHistSize = new MatOfInt(bins);
        MatOfFloat mRanges = new MatOfFloat(0, 179);
        MatOfInt mChannels = new MatOfInt(0);

        // set angle to -1 at all points where gradient magnitude is 0 i.e. where there are no edges
        // these angles will be ignored in the histogram calculation
        Mat mask = Mat.zeros(img_details.gradient_direction.size(), CvType.CV_8U);
        Core.inRange(img_details.gradient_magnitude, new Scalar(0), new Scalar(0), mask);
        img_details.gradient_direction.setTo(new Scalar(DUMMY_ANGLE), mask);
        write_Mat("angles_modified.csv", img_details.gradient_direction);

        int width_offset = RECT_WIDTH / 2;
        int height_offset = RECT_HEIGHT / 2;
        int rect_area;
        Mat prob_mat = Mat.zeros(rows, cols, CvType.CV_32F);
        double prob, max_angle_count, second_highest_angle_count, angle_diff;
        int[][] histLocs;

        // TODO: try doing this in increments of every 4 rows or columns to reduce processing times
        for (int i = 0; i < rows; i++) {
            // first calculate the row locations of the rectangle and set them to -1 
            // if they are outside the matrix bounds

            top_row = ((i - height_offset - 1) < 0) ? -1 : (i - height_offset - 1);
            bottom_row = ((i + height_offset) > rows) ? rows : (i + height_offset);

            for (int j = 0; j < cols; j++) {
                // first check if there is a gradient at this pixel
                // no processing needed if so
                if (img_details.gradient_magnitude.get(i, j)[0] == 0)
                    continue;

                // then calculate the column locations of the rectangle and set them to -1 
                // if they are outside the matrix bounds                
                left_col = ((j - width_offset - 1) < 0) ? -1 : (j - width_offset - 1);
                right_col = ((j + width_offset) > cols) ? cols : (j + width_offset);
                // TODO: do this more efficiently               

                rect_area = Core.countNonZero(img_details.gradient_magnitude.submat(Math.max(top_row, 0), bottom_row, Math.max(
                    left_col, 0), right_col));
                if (rect_area < THRESHOLD_T2) // if gradient density is below the threshold level, prob of matrix code at this pixel is 0
                    continue;
                imgWindow = img_details.gradient_direction.
                    submat(Math.max(top_row, 0), bottom_row, Math.max(left_col, 0), right_col);
                Imgproc.calcHist(Arrays.asList(imgWindow), mChannels, new Mat(), hist, mHistSize, mRanges, false);
                hist.convertTo(hist, CvType.CV_32S);
                histLocs = getMaxElements(hist);
  
                max_angle_count = histLocs[0][1];
                second_highest_angle_count = histLocs[1][1];                
                angle_diff = Math.abs(histLocs[0][0] - histLocs[1][0]) * BIN_WIDTH;
                prob = 1 - (Math.abs(angle_diff - 90) / 90.0);
                prob = prob * 2 * Math.min(max_angle_count, second_highest_angle_count) / (max_angle_count + second_highest_angle_count);
                prob_mat.put(i, j, prob);

            }  // for j
        }  // for i    

        return prob_mat;

    }

    private int[][] getMaxElements(MatOfInt histogram) {
        // returns an array of size 2 containing the indices of the highest two elements in 
        // the histogram in hist. Used by calcHist method - only works with 1D histogram
        // first element of return array is the highest and second element is second highest
        // TODO: replace this with a more efficient in-place algorithm

      
        Mat hist = histogram.clone();
        int[][] histLocs = new int[2][2];
        Core.MinMaxLocResult result = Core.minMaxLoc(hist);
        histLocs[0][0] = (int) result.maxLoc.y;
        histLocs[0][1] = (int) hist.get(histLocs[0][0], 0)[0];        

        // now set highest-val location to a low number. The previous second-highest bin is now the highest bin
        hist.put((int) result.maxLoc.y, (int) result.maxLoc.x, 0);
        result = Core.minMaxLoc(hist);
        histLocs[1][0] = (int) result.maxLoc.y;
        histLocs[1][1] = (int) hist.get(histLocs[1][0], 0)[0];

        return histLocs;
    }
}
