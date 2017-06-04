/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>
 *
 * LibLaserCut is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibLaserCut is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with LibLaserCut. If not, see <http://www.gnu.org/licenses/>.
 *
 **/
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.platform.Util;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import net.sf.corn.httpclient.HttpClient;
import net.sf.corn.httpclient.HttpResponse;

/**
 * This class implements a driver for Grbl based firmwares connected to an Octoprint host
 *
 * @author Michael Adams <zap@michaeladams.org>
 */
public class OctoPrintGrbl extends GenericGcodeDriver
{
  public OctoPrintGrbl()
  {
    setPreJobGcode(getPreJobGcode()+",M3");
    // turn off laser before returning to home position
    setPostJobGcode("M5,"+getPostJobGcode());
    // Grbl doesn't turn off laser during G0 rapids
    setBlankLaserDuringRapids(true);
    // default octopi url
    setHttpUploadUrl("http://octopi.local/api/files/local");
    // default upload method
    setUploadMethod(UPLOAD_METHOD_HTTP);
  }
  
  protected static final String SETTING_OCTOPRINT_API_KEY = "OctoPrint API key";
  
  @Override
  public String[] getPropertyKeys()
  {
    List<String> result = new LinkedList<String>();
    result.addAll(Arrays.asList(super.getPropertyKeys()));
    result.remove(GenericGcodeDriver.SETTING_HOST);
    result.remove(GenericGcodeDriver.SETTING_COMPORT);
    result.remove(GenericGcodeDriver.SETTING_BAUDRATE);
    result.remove(GenericGcodeDriver.SETTING_LINEEND);
    result.remove(GenericGcodeDriver.SETTING_IDENTIFICATION_STRING);
    result.remove(GenericGcodeDriver.SETTING_WAIT_FOR_OK);
    result.remove(GenericGcodeDriver.SETTING_TRAVEL_SPEED);
    result.remove(GenericGcodeDriver.SETTING_INIT_DELAY);
    result.remove(GenericGcodeDriver.SETTING_SERIAL_TIMEOUT);
    result.remove(GenericGcodeDriver.SETTING_UPLOAD_METHOD);
    result.remove(GenericGcodeDriver.SETTING_TRAVEL_SPEED);
    result.add(SETTING_OCTOPRINT_API_KEY);
    return result.toArray(new String[0]);
  }
  
  @Override
  public Object getProperty(String attribute) {
    if (SETTING_OCTOPRINT_API_KEY.equals(attribute)) {
      return this.getOctoPrintApiKey();
    }
    else {
      return super.getProperty(attribute);
    }
  }
  
  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_OCTOPRINT_API_KEY.equals(attribute)) {
      this.setOctoPrintApiKey((String) value);
    }
    else {
      super.setProperty(attribute, value);
    }
  }
  
  protected String octoPrintApiKey = "";
  
  public String getOctoPrintApiKey()
  {
    return octoPrintApiKey;
  }
  
  public void setOctoPrintApiKey(String octoPrintApiKey)
  {
    this.octoPrintApiKey = octoPrintApiKey;
  }

  @Override
  public String getModelName()
  {
    return "OctoPrint+Grbl Gcode Driver";
  }
  
  /**
   * Send a G0 rapid move to Grbl.
   * Doesn't include travel speed since grbl ignores that anyway.
   * 
   * @param out
   * @param x
   * @param y
   * @param resolution
   * @throws IOException 
   */
  @Override
  protected void move(PrintStream out, double x, double y, double resolution) throws IOException {
    x = isFlipXaxis() ? getBedWidth() - Util.px2mm(x, resolution) : Util.px2mm(x, resolution);
    y = isFlipYaxis() ? getBedHeight() - Util.px2mm(y, resolution) : Util.px2mm(y, resolution);
    currentSpeed = getTravel_speed();
    if (blankLaserDuringRapids)
    {
      currentPower = 0.0;
      sendLine("G0 X%f Y%f S0", x, y);
    }
    else
    {
      sendLine("G0 X%f Y%f", x, y);
    }
  }
  
  @Override
  protected void http_upload(URI url, String data, String filename) throws IOException
  {
    HttpClient client = new HttpClient(url);
    client.setContentType("multipart/form-data; boundary=----WebKitFormBoundaryDeC2E3iWbTv1PwMC");
    client.setAcceptedType("application/json, text/javascript, */*; q=0.01");
    client.putAdditionalRequestProperty("X-Api-Key", getOctoPrintApiKey());
    client.setAcceptedEncodings("gzip, deflate, br");
    client.setAcceptedLanguage("en-US,en;q=0.8");
    
    // oh wow we have to make our own multipart post content?!
    String multipart_data = "------WebKitFormBoundaryDeC2E3iWbTv1PwMC\r\n" +
      "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
      "Content-Type: application/octet-stream\r\n" +
      "\r\n" +
      data +
      "\r\n" +
      "------WebKitFormBoundaryDeC2E3iWbTv1PwMC\r\n" +
      "Content-Disposition: form-data; name=\"select\"\r\n" +
      "\r\n" +
      "true\r\n" +
      "------WebKitFormBoundaryDeC2E3iWbTv1PwMC\r\n" +
      "Content-Disposition: form-data; name=\"print\"\r\n" +
      "\r\n" +
      (isAutoPlay() ? "true" : "false") + "\r\n" +
      "------WebKitFormBoundaryDeC2E3iWbTv1PwMC--\r\n";
    
    HttpResponse response = client.sendData(HttpClient.HTTP_METHOD.POST, multipart_data);
    if (response == null || response.getCode() != 201) // hasError doesn't work for 201 response code
    {
      throw new IOException("Error during POST Request");
    }
  }

  @Override
  protected void http_play(String filename) throws IOException, URISyntaxException
  {
  }


  @Override
  public OctoPrintGrbl clone()
  {
    OctoPrintGrbl clone = new OctoPrintGrbl();
    clone.copyProperties(this);
    return clone;
  }
}
