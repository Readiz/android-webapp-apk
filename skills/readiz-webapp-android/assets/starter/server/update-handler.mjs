import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
const VERSION=/^\d{1,5}\.\d{1,5}\.\d{1,5}$/;
const HASH=/^[a-f0-9]{64}$/;
const MAX=16*1024*1024;
function regular(file,limit){const s=fs.lstatSync(file);if(!s.isFile()||s.size<1||s.size>limit)throw Error('invalid-file');return fs.readFileSync(file);}
export function readRelease(directory,packageName,version){
  if(version!==undefined&&!VERSION.test(version))throw Error('invalid-version');
  const file=version===undefined?'app.apk.json':`app-${version}.apk.json`;
  const {packageName:id,versionCode,versionName,size,sha256}=JSON.parse(regular(path.join(directory,file),4096));
  if(id!==packageName||!Number.isSafeInteger(versionCode)||versionCode<1||versionCode>2147483647||typeof versionName!=='string'||!VERSION.test(versionName)
    ||!Number.isSafeInteger(size)||size<1||size>MAX||typeof sha256!=='string'||!HASH.test(sha256)||version!==undefined&&versionName!==version)throw Error('invalid-release');
  const bytes=regular(path.join(directory,`app-${versionName}.apk`),MAX);
  if(bytes.length!==size||createHash('sha256').update(bytes).digest('hex')!==sha256)throw Error('corrupt-release');
  return {release:{packageName:id,versionCode,versionName,size,sha256},bytes};
}
// Mount behind the service's own TLS/routing. No filesystem paths or credentials are returned.
export function createUpdateHandler({directory,packageName,basePath='/'}){
  if(!path.isAbsolute(directory)||!basePath.startsWith('/')||!basePath.endsWith('/'))throw Error('invalid-options');
  return (req,res)=>{
    res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
    const send=(status,body,type='application/json')=>{const bytes=Buffer.isBuffer(body)?body:Buffer.from(JSON.stringify(body));res.writeHead(status,{'Content-Type':type,'Content-Length':bytes.length});res.end(req.method==='HEAD'?undefined:bytes);};
    if(!['GET','HEAD'].includes(req.method))return send(405,{error:'method-not-allowed'});
    let url;try{url=new URL(req.url,'http://localhost');}catch{return send(400,{error:'invalid-request'});}
    const metadata=url.pathname===basePath+'app-version';const apk=url.pathname===basePath+'app.apk';
    if(!metadata&&!apk)return send(404,{error:'not-found'});
    const keys=[...url.searchParams.keys()];
    if(metadata&&keys.length)return send(400,{error:'invalid-query'});
    const pinned=apk&&keys.length>0;
    const version=url.searchParams.get('version'),hash=url.searchParams.get('sha256');
    if(pinned&&(keys.length!==2||new Set(keys).size!==2||!keys.includes('version')||!keys.includes('sha256')||!VERSION.test(version??'')||!HASH.test(hash??'')))return send(400,{error:'invalid-release'});
    try{
      const result=readRelease(directory,packageName,pinned?version:undefined);
      if(pinned&&hash!==result.release.sha256)return send(409,{error:'release-mismatch'});
      if(metadata)return send(200,result.release);
      res.setHeader('Content-Disposition','attachment; filename="app.apk"');send(200,result.bytes,'application/vnd.android.package-archive');
    }catch{send(503,{error:'app-update-unavailable'});}
  };
}
