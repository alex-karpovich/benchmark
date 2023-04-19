[client]
%{ for ip in client_hosts ~}
${ip}
%{ endfor ~}

[kafka]
%{ for ip in kafka_hosts ~}
${ip}
%{ endfor ~}

[zookeeper]
%{ for ip in zookeeper_hosts ~}
${ip}
%{ endfor ~}