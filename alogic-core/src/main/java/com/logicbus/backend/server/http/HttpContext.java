package com.logicbus.backend.server.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.anysoft.util.IOTools;
import com.anysoft.util.KeyGen;
import com.anysoft.util.Settings;
import com.logicbus.backend.Context;

/**
 * Http请求的上下文
 * 
 * @author duanyy
 * @version 1.0.5 [20140412 duanyy]  <br>
 * - 改进消息传递模型 <br>
 * 
 * @version 1.4.0 [20141117 duanyy] <br>
 * - 将MessageDoc和Context进行合并整合 <br>
 * 
 * @version 1.6.1.1 [20141117 duanyy] <br>
 * - 增加{@link #getMethod()}实现 <br>
 * - 暴露InputStream和OutputStream <br>
 * 
 * @version 1.6.1.2 [20141118 duanyy] <br>
 * - 增加截取数据的功能 <br>
 * 
 * @version 1.6.2.1 [20141223 duanyy] <br>
 * - 增加对Comet的支持 <br>
 * 
 * @version 1.6.3.10 [20140324 duanyy] <br>
 * - 增加忽略本次输出的功能 <br>
 * 
 * @version 1.6.4.22 [20160113 duanyy] <br>
 * - 当发生错误时，细化错误信息的输出 <br>
 * 
 * @version 1.6.5.6 [20160523 duanyy] <br>
 * - 不再从MessageDoc上继承 <br>
 * - 增加报文长度 <br>
 * - 增加全局调用次序 <br>
 * 
 */

public class HttpContext extends Context {
	/**
	 * a logger of log4j
	 */
	protected final Logger logger = LogManager.getLogger(HttpContext.class);
	
	/**
	 * request
	 */
	protected HttpServletRequest request = null;
	
	/**
	 * 全局序列号
	 */
	private String globalSerial = null;	
	
	/**
	 * 调用次序
	 */
	private long globalSerialOrder = -1;
	
	/**
	 * response
	 */
	protected HttpServletResponse response = null;
	
	/**
	 * constructor
	 * @param _request HttpServletRequest
	 * @param _response HttpServletResponse
	 * @param _encoding the encoding
	 */
	public HttpContext(HttpServletRequest _request,HttpServletResponse _response,String _encoding){
		super(_encoding);
		request = _request;
		response = _response;
	}
	
	/**
	 * constructor
	 * @param _request HttpServletRequest
	 * @param _response HttpServletResponse
	 * @param _encoding the encoding
	 * @param toIntercept whether or not to intercept data from input stream 
	 */
	public HttpContext(HttpServletRequest _request,HttpServletResponse _response,String _encoding,boolean toIntercept){
		this(_request,_response,_encoding);
		
		if (toIntercept){
			InputStream in = null;
			try {
				in = request.getInputStream();
				requestRaw = readBytes(in);
			}catch (Exception ex){
				logger.error("Error when reading data from inputstream",ex);
			}finally{
				IOTools.close(in);
			}
		}
	}
	
	/**
	 * to get request
	 * @return HttpServletRequest
	 */
	public HttpServletRequest getRequest(){ return request;}	
	
	/**
	 * to get response
	 * @return HttpServletResponse
	 */
	public HttpServletResponse getResponse(){return response;}	
	
	@Override
	public String _GetValue(String _name) {
		String found = super._GetValue(_name);
		if (found == null || found.length() <= 0){
			if (request != null)
			{
				HttpSession session = request.getSession(false);
				if (session != null){
					Object obj = session.getAttribute(_name);
					if (obj != null) return obj.toString();
				}
				String value = request.getParameter(_name);
				if (value != null){
					return value;
				}
			}
		}
		return found;
	}

	@Override
	public String getClientIp() {
		/**
		 * 支持负载均衡器的X-Forwarded-For
		 */
		String ip = request.getHeader(ForwardedHeader);
		return (StringUtils.isEmpty(ip)) ? request.getRemoteHost() : ip;
	}
	
	@Override
	public String getClientRealIp(){
		return request.getRemoteHost();
	}

	@Override
	public String getHost() {
		return request.getLocalAddr() + ":" + request.getLocalPort();
	}

	@Override
	public String getRequestURI() {
		// since 1.2.0 返回整个URL
		String queryString = request.getQueryString();
		if (queryString != null && queryString.length() > 0){
			return request.getRequestURL().toString() + "?" + queryString;
		}else{
			return request.getRequestURL().toString();
		}
	}
	
	@Override
	public String getGlobalSerial() {
		if (StringUtils.isEmpty(globalSerial)){
			globalSerial = request.getHeader("GlobalSerial");
			if (StringUtils.isEmpty(globalSerial)){
				String sample = request.getParameter("sample");
				globalSerial = createGlobalSerial(StringUtils.isNotEmpty(sample) && Boolean.parseBoolean(sample));
			}
		}
		return globalSerial;
	}
	
	@Override
	public long getGlobalSerialOrder() {
		if (globalSerialOrder < 0){
			String value = request.getHeader("GlobalSerialOrder");
			if (StringUtils.isNotEmpty(value)){
				try {
					globalSerialOrder = Long.parseLong(value);
				}catch (NumberFormatException ex){
					globalSerialOrder = 1;
				}
			}else{
				globalSerialOrder = 1;
			}
		}
		return globalSerialOrder;
	}	
			
	/**
	 * 生成全局序列号
	 * <p>
	 * 根据简单的算法生成一个全部不重复的序列号。
	 * 
	 * @param sample 是否采样
	 * @return 全局序列号
	 * 
	 * @since 1.0.7
	 * 
	 */
	public static String createGlobalSerial(boolean sample){
		if (sample){
			return "s" + KeyGen.uuid(9,36);
		}else{
			return KeyGen.uuid(10, 36);
		}
	}	
	
	@Override
	public long getContentLength() {
		return msg == null ? 0 : msg.getContentLength();
	}

	@Override
	public String getRequestHeader(String id) {
		return request.getHeader(id);
	}

	@Override
	public void setResponseHeader(String id, String value) {
		response.setHeader(id, value);
	}

	@Override
	public String getRequestContentType() {
		return request.getContentType();
	}
	
	@Override
	public String getMethod() {
		return request.getMethod();
	}
	
	@Override
	public String getQueryString(){
		return request.getQueryString();
	}
	
	@Override
	public void setResponseContentType(String contentType) {
		response.setContentType(contentType);
	}

	@Override
	public InputStream getInputStream() throws IOException{
		return request.getInputStream();
	}

	@Override
	public OutputStream getOutputStream() throws IOException{
		return response.getOutputStream();
	}	
	
	/**
	 * requestRow,存放提前截取的输入数据
	 */
	private byte [] requestRaw = null;
	
	@Override
	public byte[] getRequestRaw() {
		return requestRaw;
	}
	
	@Override
	public void finish() {
		try {
			if (!isIgnore()){
				if (msg == null){
					if (getReturnCode().equals("core.ok")){
						response.sendError(404, "No message is found,check servant implemention.");
					}else{
						response.sendError(404, getReturnCode() + ":" + getReason());
					}
				}else{
					response.setCharacterEncoding(encoding);
					msg.finish(this,!cometMode());
				}
			}
		}catch (Exception ex){
			try {
				response.sendError(404, ex.getMessage());
			}catch (Exception e){
				logger.error("Error when writing result",e);
			}
		}
	}
	
	public static String ForwardedHeader = "X-Forwarded-For";
	static{
		Settings settings = Settings.get();
		ForwardedHeader = settings.GetValue("http.forwardedheader", ForwardedHeader);
	}
	
	/**
	 * 从输入流中读取字节
	 * @param in 输入流
	 * @return 字节数组
	 * @throws IOException
	 */
	public static byte [] readBytes(InputStream in) throws IOException{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		byte[] buf =new byte[1024];  
        
        int size=0;  
          
        while((size=in.read(buf))!=-1)  
        {  
            bos.write(buf,0,size);  
        }  
		
		return bos.toByteArray();
	}
}
