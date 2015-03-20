package com.seenu.tracecompass.live;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
public class MakeMeLiveTrace {
	private static final byte[] intToByteArray(int value) {
		return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16),
				(byte) (value >>> 8), (byte) value};
	}
	
	public static void main(String[] args) {
		String[] fEventTypes = new String[] {"Time", "Sine Value", "Cos Value"};
		final String fileLoc = System.getProperty("user.home") + File.separator + "Desktop"+ File.separatorChar+"traces"+File.separatorChar+"sinetrace.live";
		try (FileOutputStream fos = new FileOutputStream(fileLoc)) {
			for (int i = 0; i < fEventTypes.length; i++) {
				System.out.println(fEventTypes);
				fos.write(fEventTypes[i].getBytes());
				if (i != fEventTypes.length-1l) {
					fos.write(',');
				} else {
					fos.write('\n');
				}
			}

			int prev_sin = 0;
			int prev_cos = 0;

			for (int i = 0; i < 1000; i++) {
				fos.write(intToByteArray(i));
				System.out.print(i+",");
				double sin = Math.sin(i*Math.PI/180)*100;
				if(sin<0)
					sin = -sin;

				double cos = Math.cos(i*Math.PI/180)*100;
				if(cos<0)
					cos = -cos;

				if(i!=0){
					sin+= prev_sin;
					cos+= prev_cos;
				}
				prev_sin = (int) sin;
				prev_cos = (int) cos;

				System.out.print((int)sin+",");
				fos.write(intToByteArray((int) sin));
				System.out.println((int)cos);
				fos.write(intToByteArray((int) cos));
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				fos.flush();
			}
			
			fos.flush();
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
