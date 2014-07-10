// Simple Marmoset Uploader
// Copyright (C) 2011-2014, David H. Hovemeyer <david.hovemeyer@gmail.com>
//
// Free software, distributed under the terms of the Apache License, version 2.0
// See: http://www.apache.org/licenses/LICENSE-2.0.html

package edu.ycp.cs.marmoset.uploader.handlers;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class IOUtil {

	public static void closeQuietly(Closeable obj) {
		if (obj == null) {
			return;
		}
		
		try {
			obj.close();
		} catch (IOException e) {
			// ignore
		}
	}

	public static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[4096];
		while (true) {
			int n = in.read(buf);
			if (n < 0) {
				break;
			}
			out.write(buf, 0, n);
		}
	}
}
