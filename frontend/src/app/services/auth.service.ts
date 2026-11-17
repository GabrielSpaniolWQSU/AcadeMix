import {Injectable} from '@angular/core';
import {Router} from '@angular/router';
import {HttpClient} from '@angular/common/http';
import {tap} from 'rxjs';
@Injectable({providedIn:'root'}) export class AuthService {
 constructor(private router:Router,private http:HttpClient){}
 login(email:string,senha:string){return this.http.post('http://localhost:8080/api/auth/login',{email,senha},{withCredentials:true}).pipe(tap(r=>localStorage.setItem('sessao',JSON.stringify(r))))}
 get sessao():any{try{return JSON.parse(localStorage.getItem('sessao')||'null')}catch{return null}}
 get admin(){return this.sessao?.perfil==='ADMIN'}
 get editorAvisos(){return this.admin||this.sessao?.perfil==='COORDENADOR'}
 get inicio(){return this.sessao?.perfil==='GERENCIADOR_CERTIFICADOS'?'/certificados':'/documentos'}
 pode(area:string){
  const perfil=this.sessao?.perfil;
  if(perfil==='ADMIN')return true;
  if(perfil==='GERENCIADOR_CERTIFICADOS')return area==='certificados';
  return ['COORDENADOR','PROFESSOR'].includes(perfil)&&['documentos','atas-atividades','avisos'].includes(area);
 }
 atualizarSessao(){return this.http.get('http://localhost:8080/api/auth/me',{withCredentials:true}).pipe(tap(r=>localStorage.setItem('sessao',JSON.stringify(r))))}
 logout(){this.http.post('http://localhost:8080/api/auth/logout',{},{withCredentials:true}).subscribe({next:()=>this.sair(),error:()=>this.sair()})}
 private sair(){localStorage.removeItem('sessao');this.router.navigateByUrl('/login')}
}
