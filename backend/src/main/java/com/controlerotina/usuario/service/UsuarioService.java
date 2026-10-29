package com.controlerotina.usuario.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.shared.exception.CredenciaisInvalidasException;
import com.controlerotina.shared.exception.EmailJaCadastradoException;
import com.controlerotina.usuario.dto.AtualizarMeRequest;
import com.controlerotina.usuario.dto.CadastroUsuarioRequest;
import com.controlerotina.usuario.dto.UsuarioResponse;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public UsuarioResponse cadastrar(CadastroUsuarioRequest request) {
        String email = normalizarEmail(request.email());

        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailJaCadastradoException(email);
        }

        Instant agora = clock.instant();
        Usuario usuario = new Usuario(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.senha()),
                request.nome().trim(),
                agora,
                agora
        );

        try {
            return UsuarioResponse.from(usuarioRepository.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException ex) {
            // Corrida check-then-act: unique uk_usuario_email → 409, nunca 500.
            if (violaUniqueEmail(ex)) {
                throw new EmailJaCadastradoException(email);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarMe(UUID usuarioId) {
        return UsuarioResponse.from(buscarPorId(usuarioId));
    }

    @Transactional
    public UsuarioResponse atualizarMe(UUID usuarioId, AtualizarMeRequest request) {
        Usuario usuario = buscarPorId(usuarioId);

        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }

        String novoNome = request.nome();
        String novoEmail = request.email() != null ? normalizarEmail(request.email()) : null;
        String novaSenhaHash = request.novaSenha() != null
                ? passwordEncoder.encode(request.novaSenha())
                : null;

        if (novoEmail != null && !novoEmail.equals(usuario.getEmail())
                && usuarioRepository.existsByEmail(novoEmail)) {
            throw new EmailJaCadastradoException(novoEmail);
        }

        usuario.atualizar(novoNome, novoEmail, novaSenhaHash, clock.instant());
        try {
            usuarioRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (violaUniqueEmail(ex)) {
                throw new EmailJaCadastradoException(novoEmail != null ? novoEmail : usuario.getEmail());
            }
            throw ex;
        }
        return UsuarioResponse.from(usuario);
    }

    private Usuario buscarPorId(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(CredenciaisInvalidasException::new);
    }

    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static boolean violaUniqueEmail(DataIntegrityViolationException ex) {
        String mensagem = mensagemCompleta(ex).toLowerCase();
        return mensagem.contains("uk_usuario_email");
    }

    private static String mensagemCompleta(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable atual = ex;
        while (atual != null) {
            if (atual.getMessage() != null) {
                sb.append(' ').append(atual.getMessage());
            }
            atual = atual.getCause();
        }
        return sb.toString();
    }
}
