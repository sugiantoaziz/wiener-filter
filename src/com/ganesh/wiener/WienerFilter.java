package com.ganesh.wiener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public class WienerFilter {
	private double[] signal;
	private double[] noise;
	private int windowSize;

	public WienerFilter(double[] signal, double[] noise, int windowSize) {
		this.signal = signal;
		this.noise = noise;
		this.windowSize = windowSize;
	}

	public double[] applyFilter() {
		int n = signal.length;
		double[] filteredSignal = new double[n];
		double[] localMean = new double[n];
		double[] localVariance = new double[n];
		double globalVariance = calculateVariance(noise);

		for (int i = 0; i < n; i++) {
			int start = Math.max(0, i - windowSize / 2);
			int end = Math.min(n - 1, i + windowSize / 2);
			double[] window = Arrays.copyOfRange(signal, start, end + 1);

			localMean[i] = calculateMean(window);
			localVariance[i] = calculateVariance(window);
		}

		for (int i = 0; i < n; i++) {
			double noiseVariance = Math.max(localVariance[i] - globalVariance, 0);
			double gain = noiseVariance / (noiseVariance + globalVariance);
			filteredSignal[i] = localMean[i] + gain * (signal[i] - localMean[i]);
		}

		return filteredSignal;
	}

	private double calculateMean(double[] data) {
		double sum = 0.0;
		for (double d : data) {
			sum += d;
		}
		return sum / data.length;
	}

	private double calculateVariance(double[] data) {
		double mean = calculateMean(data);
		double sum = 0.0;
		for (double d : data) {
			sum += Math.pow(d - mean, 2);
		}
		return sum / data.length;
	}

	public static void main(String[] args) {
		try {
			// Capture audio data
			AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
			TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
			line.open(format);
			line.start();

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int bytesRead;

			System.out.println("Recording...");
			while (out.size() < 44100 * 5) { // Record for 5 seconds
				bytesRead = line.read(buffer, 0, buffer.length);
				out.write(buffer, 0, bytesRead);
			}

			line.stop();
			line.close();
			System.out.println("Recording stopped.");

			byte[] audioBytes = out.toByteArray();
			double[] signal = byteArrayToDoubleArray(audioBytes);

			// Assuming noise is a separate captured data or predefined
			double[] noise = new double[signal.length]; // Replace with actual noise data

			int windowSize = 5;
			WienerFilter wf = new WienerFilter(signal, noise, windowSize);
			double[] filteredSignal = wf.applyFilter();

			System.out.println("Filtered Signal: " + Arrays.toString(filteredSignal));

			// Play the filtered signal
			byte[] filteredBytes = doubleArrayToByteArray(filteredSignal);
			playAudio(filteredBytes, format);

		} catch (LineUnavailableException | IOException e) {
			e.printStackTrace();
		}
	}

	private static double[] byteArrayToDoubleArray(byte[] bytes) {
		double[] doubles = new double[bytes.length / 2];
		for (int i = 0; i < doubles.length; i++) {
			doubles[i] = ((bytes[2 * i] << 8) | (bytes[2 * i + 1] & 0xFF)) / 32768.0;
		}
		return doubles;
	}

	private static byte[] doubleArrayToByteArray(double[] doubles) {
		byte[] bytes = new byte[doubles.length * 2];
		for (int i = 0; i < doubles.length; i++) {
			int temp = (int) (doubles[i] * 32768);
			bytes[2 * i] = (byte) (temp >> 8);
			bytes[2 * i + 1] = (byte) (temp & 0xFF);
		}
		return bytes;
	}

	private static void playAudio(byte[] audioBytes, AudioFormat format) throws LineUnavailableException, IOException {
		ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
		AudioInputStream ais = new AudioInputStream(bais, format, audioBytes.length / format.getFrameSize());
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
		line.open(format);
		line.start();

		byte[] buffer = new byte[1024];
		int bytesRead;
		while ((bytesRead = ais.read(buffer, 0, buffer.length)) != -1) {
			line.write(buffer, 0, bytesRead);
		}

		line.drain();
		line.close();
	}
}
