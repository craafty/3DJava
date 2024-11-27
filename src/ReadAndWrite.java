import java.io.*;
import java.util.Scanner;

public class ReadAndWrite {

    public static void main(String[] args) throws IOException {
        ReadAndWrite r = new ReadAndWrite();
        int[] temp = r.txtToArray("textures/T_20.h");
        r.arrayToTxt(temp, "textures/T_20.txt");
    }
    public ReadAndWrite() {}
    public int[] txtToArray(String filePath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        int height = Integer.parseInt(reader.readLine().trim().split(" ")[2]);
        int width = Integer.parseInt(reader.readLine().trim().split(" ")[2]);
        reader.readLine();
        reader.readLine();
        reader.readLine();
        int[] intArray = new int[height*3*width+2];
        intArray[0]=height;
        intArray[1]=width;
        for (int h = 0; h < height*3; h++) {
            int[] temp = strToInt(reader.readLine().trim().split(", "), h==height*3-1);
            for(int i=0;i<temp.length; i++){
                System.out.print(temp[i]+" ");
            }
            System.out.println();
            for(int w=0; w < width; w++) {
                intArray[h*width+w+2] = temp[w];
            }
        }
        reader.close();
        return intArray;
    }
    public int[] strToInt(String[] array, boolean isLastRow) {
        //remove weird comma thingy
        if(!isLastRow){
            array[array.length-1] = array[array.length-1].substring(0,array[array.length-1].length()-1);
        }
        int[] intArray = new int[array.length];
        for(int i = 0; i < array.length; i++){
            intArray[i] = Integer.parseInt(array[i]);
        }
        return intArray;
    }
    public void arrayToTxt(int[] array, String filePath) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
        int height = array[0];
        int width = array[1];
        writer.write(Integer.toString(height));
        writer.newLine();
        writer.write(Integer.toString(width));
        writer.newLine();

        for (int h=0;h<height*3;h++) {
            for(int w=0;w<width;w++){
                writer.write(Integer.toString(array[h*width+w+2])+" ");
            }
            writer.newLine();
        }
        writer.close();
    }
}
