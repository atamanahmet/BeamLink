package com.atamanahmet.beamlink.peer.repository;

import com.atamanahmet.beamlink.peer.domain.Peer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PeerRepository extends JpaRepository<Peer, UUID> {}