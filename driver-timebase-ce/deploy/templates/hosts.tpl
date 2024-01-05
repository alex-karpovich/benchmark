[client]
%{ for ip in client_hosts ~}
${ip}
%{ endfor ~}

[timebase]
%{ for ip in timebase_hosts ~}
${ip}
%{ endfor ~}
