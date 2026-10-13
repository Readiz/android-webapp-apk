import fs from 'node:fs';import http from 'node:http';import path from 'node:path';import {parseArgs} from 'node:util';
import {createUpdateHandler} from './update-handler.mjs';
const {values}=parseArgs({options:{config:{type:'string',default:'app.json'},releases:{type:'string'},port:{type:'string',default:'8080'},host:{type:'string',default:'127.0.0.1'}}});
if(!values.releases)throw Error('Required: --releases /absolute/release-directory');
const cfg=JSON.parse(fs.readFileSync(values.config,'utf8'));const basePath=new URL(cfg.updateBaseUrl).pathname;
const server=http.createServer(createUpdateHandler({directory:path.resolve(values.releases),packageName:cfg.applicationId,basePath}));
server.listen(Number(values.port),values.host,()=>console.log(`APK update server: http://${values.host}:${server.address().port}${basePath}`));
