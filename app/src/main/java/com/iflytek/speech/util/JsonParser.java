package com.iflytek.speech.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONException;

import android.util.Log;

/**
 * Json结果解析类
 */
public class JsonParser {

	public static String parseIatResult(String json) {
		StringBuffer ret = new StringBuffer();
		try {
			JSONTokener tokener = new JSONTokener(json);
			JSONObject joResult = new JSONObject(tokener);

			JSONArray words = joResult.getJSONArray("ws");
			for (int i = 0; i < words.length(); i++) {
				// 转写结果词，默认使用第一个结果
				JSONArray items = words.getJSONObject(i).getJSONArray("cw");
				JSONObject obj = items.getJSONObject(0);
				ret.append(obj.getString("w"));
//				如果需要多候选结果，解析数组其他字段
//				for(int j = 0; j < items.length(); j++)
//				{
//					JSONObject obj = items.getJSONObject(j);
//					ret.append(obj.getString("w"));
//				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} 
		return ret.toString();
	}
	
	public static String parseGrammarResult(String json) {
		StringBuffer ret = new StringBuffer();
		try {
			JSONTokener tokener = new JSONTokener(json);
			JSONObject joResult = new JSONObject(tokener);

			JSONArray words = joResult.getJSONArray("ws");
			for (int i = 0; i < words.length(); i++) {
				JSONArray items = words.getJSONObject(i).getJSONArray("cw");
				for(int j = 0; j < items.length(); j++)
				{
					JSONObject obj = items.getJSONObject(j);
					if(obj.getString("w").contains("nomatch"))
					{
						ret.append("没有匹配结果.");
						return ret.toString();
					}
					ret.append("【结果】" + obj.getString("w"));
					ret.append("【置信度】" + obj.getInt("sc"));
					ret.append("\n");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			ret.append("没有匹配结果.");
		} 
		return ret.toString();
	}
	
	public static String parseLocalGrammarResult(String json) {
		StringBuffer ret = new StringBuffer();
		try {
			JSONTokener tokener = new JSONTokener(json);
			JSONObject joResult = new JSONObject(tokener);

			JSONArray words = joResult.getJSONArray("ws");
			for (int i = 0; i < words.length(); i++) {
				JSONArray items = words.getJSONObject(i).getJSONArray("cw");
				for(int j = 0; j < items.length(); j++)
				{
					JSONObject obj = items.getJSONObject(j);
					if(obj.getString("w").contains("nomatch"))
					{
						ret.append("没有匹配结果.");
						return ret.toString();
					}
					ret.append("【结果】" + obj.getString("w"));
					ret.append("\n");
				}
			}
			ret.append("【置信度】" + joResult.optInt("sc"));

		} catch (Exception e) {
			e.printStackTrace();
			ret.append("没有匹配结果.");
		} 
		return ret.toString();
	}
	
	@SuppressWarnings("finally")
	public static String parseUnderstandResult(String json){
		String text_data = null;
		StringBuffer ret = new StringBuffer();
		JSONTokener tokener = new JSONTokener(json);
		try {
			JSONObject joResult = new JSONObject(tokener);
			switch(joResult.getString("operation").toString()){
			case "CALL":
				text_data = "需要打电话";
			    break;
				
			case "OPEN":
				text_data = "需要查找应网页";
				break; 
			case "SEND":
				text_data = "发送短信";
				break; 
			case "QUERY":
				switch(joResult.get("service").toString()){
				case "weather":
					JSONObject org = joResult.getJSONObject("data").getJSONObject("result");
					text_data = org.getString("city")+org.getString("date")+org.getString("wind")+"风速"+org.getString("windLevel")+"温度"+org.getString("tempRange");
				    break;		
				}
				break;
				
			case "PLAY":
				switch(joResult.get("service").toString()){
				case "music":
					JSONObject org = joResult.getJSONObject("data").getJSONObject("result");
					
					text_data = "暂时还没有设置"+org.getString("singer").toString() + "的"+org.getString("name");
				    break;
				}
			case "ANSWER":
				text_data = joResult.getJSONObject("answer").getString("text");
				break;
			case "LAUNCH":
				if(joResult.get("operation").equals("app")){
					text_data = joResult.getJSONObject("moreResults").getJSONObject("semantic").getJSONObject("slots").getString("name");
					break;	
				}
			case "CLOSE":
				text_data = joResult.getJSONObject("text").toString();
				
				break;
			case "PAUSE":
				text_data = joResult.getJSONObject("text").toString();
				break;
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}finally{
			return text_data;
		}	
	}
}
