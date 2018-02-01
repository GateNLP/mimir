<%@page import="java.io.*"%>
<%!
String formatDiskSpace(double bytes) {
  String[] suffixes = {"", "k", "M", "G", "T"};
  int s = 0;
  while(bytes > 1000 && s < suffixes.length - 1) {
    bytes /= 1000;
    s++;
  }
  return String.format("%.1f ", bytes) + suffixes[s] + "B";
}
%>
<html>
<head>
<title>GATECloud.net</title>
</head>
<body>
<h1>This is a GATECloud.net service</h1>
<p>This server is provided by
<a href="http://gatecloud.net/">GATECloud.net</a>.</p>
<%
File dataPartition = new File("/data");
if(dataPartition.isDirectory()) {
  try {
    long total = dataPartition.getTotalSpace();
    long free = dataPartition.getFreeSpace();
    long used = total - free;
%><h2>Data volume details</h2>
<table border="0">
  <tr><td>Total size</td><td><%= formatDiskSpace(total) %></td></tr>
  <tr><td>Free space</td><td><%= formatDiskSpace(free) %></td></tr>
  <tr><td>Usage</td><td><%= (int)(((double)used) / total * 100) %>%</td></tr>
</table>
<%
  }
  catch(Exception e) {
    e.printStackTrace();
  }
}
%>
</body>
</html>
