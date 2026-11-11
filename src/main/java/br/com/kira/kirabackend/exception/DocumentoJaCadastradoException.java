package br.com.kira.kirabackend.exception;

public class DocumentoJaCadastradoException extends RuntimeException {
    public DocumentoJaCadastradoException(String mensagem) {
        super(mensagem);
    }
}