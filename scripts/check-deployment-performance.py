import subprocess,os,re,json,sys
base='https://14.103.81.73'
common=['curl','--noproxy','*','--cacert',os.path.expanduser('~/.config/shimo/server-ip.crt'),'--http2','-sS','--max-time','60']
html=subprocess.check_output(common+[base+'/shimo/']).decode()
js=re.search(r'src="(/shimo/assets/[^\"]+\.js)"',html)[1]
css_path=re.search(r'href="(/shimo/assets/[^\"]+\.css)"',html)[1]
css=subprocess.check_output(common+['--compressed',base+css_path]).decode()
font=re.search(r'/shimo/assets/fonts/quanzi-kai/core\.[0-9a-f]{12}\.woff2',css)
if not font:raise SystemExit('FAIL: hashed WOFF2 core missing from production CSS')
font=font[0]
fail=[]
for path in [js,css_path,font,'/shimo/','/shimo/api/auth/session']:
 h=subprocess.check_output(common+['-I','-H','Accept-Encoding: gzip',base+path]).decode()
 headers={};first=h.splitlines()[0]
 for line in h.splitlines()[1:]:
  if ':' in line:
   k,v=line.split(':',1);headers[k.lower()]=v.strip()
 if not first.startswith('HTTP/2 200'):fail.append(path+': expected HTTP/2 200')
 if path==js and headers.get('content-encoding')!='gzip':fail.append('JS gzip absent')
 cache=headers.get('cache-control','')
 if path==font:
  if 'public' not in cache or 'max-age=31536000' not in cache or 'immutable' not in cache or 'no-store' in cache:fail.append(path+': immutable font cache absent')
 elif '/assets/' in path:
  if 'public' not in cache or 'max-age=3600' not in cache or 'no-store' in cache:fail.append(path+': static cache absent')
 elif 'no-store' not in cache:fail.append(path+': private cache protection missing')
 print(json.dumps({'path':path,'http':first,'encoding':headers.get('content-encoding'),'cache':cache}))
r=subprocess.check_output(common+['--compressed','-o','/dev/null','-w','%{http_version} bytes=%{size_download} ttfb=%{time_starttransfer}s total=%{time_total}s',base+js]).decode();print('JS measurement:',r)
if fail:print('FAIL:', '; '.join(fail));sys.exit(1)
print('PASS: HTTP/2 + gzip + static cache, without caching HTML/session API')
