package edu.ycp.cs.marmoset.uploader.handlers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.contrib.ssl.EasySSLProtocolSocketFactory;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import edu.ycp.cs.marmoset.uploader.Activator;

public abstract class Uploader {
	
	static {
		Protocol easyHttps = new Protocol("https", (ProtocolSocketFactory) new EasySSLProtocolSocketFactory(), 8443);
		Protocol.registerProtocol("https", easyHttps);
	}

	public static Result sendZipFileToServer(Properties submitProperties, File zipFile, String username, String password) throws HttpException, IOException {
		PostMethod post = null;
		HttpClient client = null;
		
		Matcher m = SubmitProjectHandler.SUBMIT_URL_PATTERN.matcher(submitProperties.getProperty(SubmitProjectHandler.PROP_SUBMIT_URL));
		if (!m.matches()) {
			throw new IllegalStateException(); // we've already verified that it's a match
		}
		
		String server = m.group(1);
		String hostName = m.group(2);
		if (hostName.indexOf(':') >= 0) {
			// host name is qualified with a port number: get rid of it
			hostName = hostName.substring(0, hostName.indexOf(':'));
		}
		
		// Submit via the BlueJ submitter servlet, which is simpler than the standard Eclipse servlet
		String url = server + "/bluej/SubmitProjectViaBlueJSubmitter";
		
		
		try {
			post = new PostMethod(url);
			post.getParams().setBooleanParameter(HttpMethodParams.USE_EXPECT_CONTINUE, true);
			
			
			List<Part> parts = new ArrayList<Part>();
			
			// Add form parameters
			parts.add(new StringPart("campusUID", username));
			parts.add(new StringPart("password", password));
			parts.add(new StringPart("submitClientTool", "SimpleMarmosetUploader"));
			parts.add(new StringPart("submitClientVersion", Activator.getDefault().getBundle().getVersion().toString()));
			// All submit properties except the submit URL must be added as parameters
			for (String prop : SubmitProjectHandler.REQUIRED_PROPERTIES) {
				if (!prop.equals(SubmitProjectHandler.PROP_SUBMIT_URL)) {
					parts.add(new StringPart(prop, submitProperties.getProperty(prop)));
				}
			}
			
			// Add the file part
			parts.add(new FilePart("submittedFiles", zipFile));
			
			MultipartRequestEntity entity = new MultipartRequestEntity(parts.toArray(new Part[parts.size()]), post.getParams());
			
			post.setRequestEntity(entity);
			
			client = new HttpClient();
			
//			// Configure for easy SSL (allow use of self-signed certificates)
//			Protocol easyHttps = new Protocol("https", (ProtocolSocketFactory) new EasySSLProtocolSocketFactory(), 443);
//			client.getHostConfiguration().setHost(hostName, 443, easyHttps);
//
//			Protocol easyHttps2 = new Protocol("https", (ProtocolSocketFactory) new EasySSLProtocolSocketFactory(), 8443);
//			client.getHostConfiguration().setHost(hostName, 8443, easyHttps2);
			
			Result result = new Result();
			
			result.httpCode = client.executeMethod(post);
			result.responseBody = post.getResponseBodyAsString();
			
			return result;
		} finally {
			if (post != null) {
				post.releaseConnection();
			}
			if (client != null) {
				client.getHttpConnectionManager().closeIdleConnections(0L);
			}
			
		}
	}

}
