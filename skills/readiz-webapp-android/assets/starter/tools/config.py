"""Configuration shared by the generator and standalone build/release tools."""
import json,re
from pathlib import Path
from urllib.parse import urlsplit,unquote
FIELDS={'appName','applicationId','versionCode','versionName','homeUrl','internalUrls','updateBaseUrl','signingAlias'}
VERSION=r'[0-9]{1,5}\.[0-9]{1,5}\.[0-9]{1,5}'
def https(value,base=False):
    if not isinstance(value,str) or len(value)>2048 or any(ord(c)<33 or ord(c)>126 for c in value):raise ValueError('Use an ASCII HTTPS URL (percent-encode non-ASCII paths)')
    u=urlsplit(value)
    if u.scheme!='https' or not u.hostname or u.username is not None or u.password is not None or u.port not in (None,443) or u.fragment:raise ValueError('Expected HTTPS URL without credentials or fragment')
    if not re.fullmatch(r'[a-z0-9.-]+',u.hostname) or '..' in unquote(u.path) or any(x in unquote(u.path) for x in ('\\','%')) or re.search(r'%(2f|5c)',u.path,re.I):raise ValueError('Invalid host or ambiguous path')
    if base and u.query:raise ValueError('Base URL cannot have a query')
    return u

def validate(c):
    if not isinstance(c,dict) or set(c)!=FIELDS:raise ValueError('Expected configuration fields: '+', '.join(sorted(FIELDS)))
    if not isinstance(c['appName'],str) or not 1<=len(c['appName'])<=60 or any(ord(x)<32 or x in "<>&\\\"'" for x in c['appName']):raise ValueError('Invalid app name')
    if not isinstance(c['applicationId'],str) or len(c['applicationId'])>150 or not re.fullmatch(r'[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){2,}',c['applicationId']):raise ValueError('Use a lowercase applicationId with at least three segments')
    if type(c['versionCode']) is not int or not 1<=c['versionCode']<=2147483647:raise ValueError('Invalid versionCode')
    if not isinstance(c['versionName'],str) or not re.fullmatch(VERSION,c['versionName']):raise ValueError('Use a numeric three-part versionName')
    if not isinstance(c['signingAlias'],str) or not re.fullmatch(r'[a-z][a-z0-9-]{0,63}',c['signingAlias']):raise ValueError('Invalid signingAlias')
    home=https(c['homeUrl']);update=https(c['updateBaseUrl'],True)
    if not update.path.endswith('/'):raise ValueError('updateBaseUrl must end in /')
    allowed=c['internalUrls']
    if not isinstance(allowed,list) or not 1<=len(allowed)<=16 or len(set(allowed))!=len(allowed):raise ValueError('Expected 1 to 16 unique internal URLs')
    matched=False
    for value in allowed:
        u=https(value,True)
        if u.path not in ('','/') and u.path.endswith('/'):raise ValueError('Internal path prefixes must not end in /')
        if home.hostname==u.hostname and (u.path in ('','/') or home.path==u.path or home.path.startswith(u.path+'/')):matched=True
    if not matched or home.path.lower().endswith('.apk'):raise ValueError('homeUrl must be inside internalUrls')
    return c

def load(path):return validate(json.loads(Path(path).read_text()))
def encoded(value):return (json.dumps(value,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode()
